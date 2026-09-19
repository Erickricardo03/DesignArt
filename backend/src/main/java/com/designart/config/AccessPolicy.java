package com.designart.config;

import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Regra ÚNICA de "esta conta pode operar agora?", consultada no login e a
 * CADA requisição (JwtAuthenticationFilter), sempre contra o estado ATUAL do
 * banco — assim desativar um usuário ou suspender um tenant invalida também
 * os JWT já emitidos. Falha fechada: qualquer dúvida = negado.
 * <p>
 * Enforcement apenas; o gerenciamento de status do tenant (billing, telas de
 * administração) pertence às fases seguintes.
 */
@Component
@RequiredArgsConstructor
public class AccessPolicy {

    public static final String TENANT_ATIVO = "ATIVO";

    private final TenantRepository tenantRepository;

    public boolean podeOperar(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getAtivo())) {
            return false;
        }
        // Usuário sem tenant (ex.: futuro SUPER_ADMIN) não depende de status de tenant.
        if (user.getTenantId() == null) {
            return true;
        }
        return tenantRepository.findById(user.getTenantId())
                .map(Tenant::getStatus)
                .map(TENANT_ATIVO::equals)
                .orElse(false); // tenant inexistente = negado
    }
}
