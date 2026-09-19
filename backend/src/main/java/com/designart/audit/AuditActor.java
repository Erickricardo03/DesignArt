package com.designart.audit;

import com.designart.model.User;
import com.designart.security.EmailAddress;
import com.designart.security.Role;

/**
 * QUEM realizou a ação, com SNAPSHOT do estado relevante no momento do evento
 * (e-mail, role e tenant), para a trilha continuar compreensível mesmo que o
 * usuário mude depois.
 * <p>
 * O único texto livre aceito é o e-mail, e ele PRECISA ter formato de e-mail
 * válido (rejeita, por exemplo, uma senha ou token digitado num campo qualquer).
 * Na prática vem sempre de {@link #of(User)}, isto é, do banco.
 */
public record AuditActor(AuditActorType type, Long userId, String email, Role role, Long tenantId) {

    public AuditActor {
        if (type == null) {
            throw new IllegalArgumentException("Tipo de ator obrigatório.");
        }
        if (email != null && EmailAddress.normalizeOrNull(email) == null) {
            throw new IllegalArgumentException("Snapshot de e-mail do ator inválido.");
        }
    }

    public static AuditActor of(User user) {
        return new AuditActor(AuditActorType.USER, user.getId(), user.getEmail(), user.getRole(), user.getTenantId());
    }

    /** Ação sem usuário autenticado (ex.: tentativa de login). */
    public static AuditActor anonymous() {
        return new AuditActor(AuditActorType.ANONYMOUS, null, null, null, null);
    }

    /** Ação do próprio sistema (rotinas internas). */
    public static AuditActor system() {
        return new AuditActor(AuditActorType.SYSTEM, null, null, null, null);
    }
}
