package com.designart.service;

import com.designart.exception.ForbiddenOperationException;
import com.designart.exception.InvalidRequestException;
import com.designart.security.EmailAddress;
import com.designart.security.Permission;
import com.designart.security.Role;

import java.util.HashSet;
import java.util.Set;

/** Regras de validação compartilhadas entre a gestão de usuários e os convites. */
final class UserRules {

    static final String MSG_OPERACAO_NEGADA = "Operação não permitida.";

    private UserRules() {
    }

    static String emailValido(String raw) {
        String email = EmailAddress.normalizeOrNull(raw);
        if (email == null) {
            throw new InvalidRequestException("Informe um e-mail válido.");
        }
        return email;
    }

    /** Administrador de tenant jamais cria/promove SUPER_ADMIN (só a administração global, em etapa futura). */
    static void exigirRoleDeTenant(Role role) {
        if (role == null || !role.belongsToTenant()) {
            throw new ForbiddenOperationException(MSG_OPERACAO_NEGADA);
        }
    }

    /** TENANT_ADMIN tem todas as permissões implicitamente (nada é gravado); USER só as explícitas. */
    static Set<Permission> permissoesEfetivas(Role role, Set<Permission> solicitadas) {
        if (role != Role.USER || solicitadas == null) {
            return new HashSet<>();
        }
        return new HashSet<>(solicitadas);
    }
}
