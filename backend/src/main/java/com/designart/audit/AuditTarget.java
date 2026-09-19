package com.designart.audit;

import com.designart.model.User;
import com.designart.security.EmailAddress;

/**
 * SOBRE quem/o quê a ação incidiu (tenant afetado e usuário alvo, com snapshot do
 * e-mail). Sempre construído a partir de entidades já persistidas — nunca de
 * texto digitado por quem faz a requisição. O único texto livre aceito é o
 * e-mail, e precisa ter formato de e-mail válido.
 */
public record AuditTarget(Long tenantId, Long userId, String email, AuditEntityType entityType, Long entityId) {

    public AuditTarget {
        if (email != null && EmailAddress.normalizeOrNull(email) == null) {
            throw new IllegalArgumentException("Snapshot de e-mail do alvo inválido.");
        }
    }

    public static AuditTarget user(User user) {
        return new AuditTarget(user.getTenantId(), user.getId(), user.getEmail(), AuditEntityType.USER, user.getId());
    }

    /** Sem alvo identificável (ex.: falha de login para conta inexistente). */
    public static AuditTarget none() {
        return new AuditTarget(null, null, null, null, null);
    }
}
