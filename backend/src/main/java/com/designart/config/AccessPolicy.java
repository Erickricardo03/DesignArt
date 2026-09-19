package com.designart.config;

import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.repository.TenantRepository;
import com.designart.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Regra ÚNICA de "esta conta pode operar agora?", consultada no login e a
 * CADA requisição (JwtAuthenticationFilter), sempre contra o estado ATUAL do
 * banco. Falha fechada: qualquer dúvida = negado.
 * <ul>
 *   <li>usuário precisa estar ativo e ter role;</li>
 *   <li><b>SUPER_ADMIN &lt;=&gt; tenant_id NULL</b>: SUPER_ADMIN só opera sem tenant;
 *       qualquer outro role sem tenant (ou SUPER_ADMIN com tenant) é NEGADO —
 *       tenant_id nulo NÃO transforma ninguém em SUPER_ADMIN;</li>
 *   <li>usuário de tenant só opera se o tenant existir e estiver ATIVO.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AccessPolicy {

    public static final String TENANT_ATIVO = "ATIVO";

    private final TenantRepository tenantRepository;

    public boolean podeOperar(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getAtivo()) || user.getRole() == null) {
            return false;
        }
        if (user.getRole() == Role.SUPER_ADMIN) {
            return user.getTenantId() == null; // SUPER_ADMIN com tenant é inconsistente: negado
        }
        if (user.getTenantId() == null) {
            return false; // USER/TENANT_ADMIN sem tenant: negado
        }
        return tenantRepository.findById(user.getTenantId())
                .map(Tenant::getStatus)
                .map(TENANT_ATIVO::equals)
                .orElse(false); // tenant inexistente = negado
    }
}
