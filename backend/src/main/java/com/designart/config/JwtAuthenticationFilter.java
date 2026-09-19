package com.designart.config;

import com.designart.model.User;
import com.designart.repository.UserRepository;
import com.designart.security.AuthoritiesFactory;
import com.designart.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Autentica cada requisição a partir do JWT, sempre contra o estado ATUAL do
 * banco. Falha fechada: qualquer irregularidade deixa a requisição anônima
 * (401 nos endpoints protegidos), sem detalhes.
 * <p>
 * Passos: assinatura + expiração -> userId (sub) -> usuário no banco ->
 * token_version igual ao atual -> AccessPolicy (ativo, regra SUPER_ADMIN/tenant,
 * tenant ATIVO) -> authorities construídas do banco -> TenantContext do usuário.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final AccessPolicy accessPolicy;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            autenticarSePresente(request);
            filterChain.doFilter(request, response);
        } finally {
            // Essencial: threads de servlet são reaproveitadas entre requisições.
            // Sem isso, o tenant de uma requisição poderia "vazar" para a próxima
            // processada pela mesma thread.
            TenantContext.clear();
        }
    }

    private void autenticarSePresente(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        final String jwt = authHeader.substring(7);

        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                return;
            }
            Claims claims = jwtUtil.parse(jwt); // assinatura + expiração
            Long userId = JwtUtil.userId(claims);
            Integer tokenVersion = JwtUtil.tokenVersion(claims);
            if (userId == null || tokenVersion == null) {
                return;
            }

            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                return;
            }
            User user = userOptional.get();

            // token_version diferente = sessão revogada (senha alterada, logout forçado, etc.).
            if (tokenVersion != user.getTokenVersion()) {
                return;
            }
            // Usuário ativo, regra SUPER_ADMIN <=> sem tenant, tenant ATIVO.
            if (!accessPolicy.podeOperar(user)) {
                return;
            }

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    String.valueOf(user.getId()), // principal = userId
                    null,
                    AuthoritiesFactory.from(user)
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // Tenant sempre derivado do usuário carregado do banco NESTA
            // requisição — nunca de um claim do token ou de valor do cliente.
            // SUPER_ADMIN fica com contexto vazio (falha fechada em tudo que é tenant-scoped).
            TenantContext.set(user.getTenantId());
        } catch (Exception e) {
            // Token inválido/expirado/adulterado: segue sem autenticação (e sem tenant).
        }
    }
}
