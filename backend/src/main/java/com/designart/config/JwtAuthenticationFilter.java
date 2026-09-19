package com.designart.config;

import com.designart.model.User;
import com.designart.repository.UserRepository;
import com.designart.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            final String username = jwtUtil.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<User> userOptional = userRepository.findByUsername(username);

                // Estado ATUAL do banco a cada requisição: usuário desativado ou tenant
                // suspenso/inativo deixa de autenticar mesmo com JWT ainda válido (falha
                // fechada; a requisição segue anônima e recebe 401 sem detalhes).
                if (userOptional.isPresent() && jwtUtil.validateToken(jwt, username)
                        && accessPolicy.podeOperar(userOptional.get())) {
                    User user = userOptional.get();
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
                    if (user.getPermissoes() != null) {
                        user.getPermissoes().forEach(permissao ->
                                authorities.add(new SimpleGrantedAuthority("PERM_" + permissao)));
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            user.getUsername(),
                            null,
                            authorities
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Tenant sempre derivado do usuário carregado do banco NESTA
                    // requisição — nunca de um claim do token ou de qualquer
                    // valor enviado pelo cliente.
                    TenantContext.set(user.getTenantId());
                }
            }
        } catch (Exception e) {
            // Se o token for inválido, segue sem autenticação (e sem tenant).
        }
    }
}
