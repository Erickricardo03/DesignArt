package com.designart.security;

import com.designart.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

/**
 * Constrói as authorities de uma requisição a partir do estado ATUAL do
 * usuário no banco (nunca de claims do JWT):
 * <ul>
 *   <li>sempre {@code ROLE_<role>};</li>
 *   <li>TENANT_ADMIN: TODAS as {@code PERM_*} (acesso implícito às permissões do tenant);</li>
 *   <li>USER: somente as {@code PERM_*} explicitamente atribuídas;</li>
 *   <li>SUPER_ADMIN: nenhuma {@code PERM_*} — permissões são de tenant e ele não opera dados de tenant.</li>
 * </ul>
 */
public final class AuthoritiesFactory {

    private AuthoritiesFactory() {
    }

    public static List<GrantedAuthority> from(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Role role = user.getRole();
        if (role == null) {
            return authorities; // sem role = sem nenhuma authority (falha fechada)
        }
        authorities.add(new SimpleGrantedAuthority(role.authority()));
        switch (role) {
            case TENANT_ADMIN -> {
                for (Permission p : Permission.values()) {
                    authorities.add(new SimpleGrantedAuthority(p.authority()));
                }
            }
            case USER -> {
                if (user.getPermissoes() != null) {
                    for (Permission p : user.getPermissoes()) {
                        authorities.add(new SimpleGrantedAuthority(p.authority()));
                    }
                }
            }
            case SUPER_ADMIN -> {
                // Intencionalmente nenhuma permissão de tenant.
            }
        }
        return authorities;
    }
}
