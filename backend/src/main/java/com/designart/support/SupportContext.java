package com.designart.support;

import java.util.UUID;

/**
 * Contexto de uma Support Session validada. SEPARADO do TenantContext (que continua sendo exclusivamente de
 * usuários reais do tenant). Identidade: SUPER_ADMIN real ({@code superAdminUserId}); o tenant é só o ALVO.
 */
public record SupportContext(Long supportSessionId, UUID publicId, Long superAdminUserId, Long targetTenantId,
                             SupportMode mode) {

    private static final ThreadLocal<SupportContext> CURRENT = new ThreadLocal<>();

    static void set(SupportContext ctx) {
        CURRENT.set(ctx);
    }

    /** Contexto de suporte da requisição corrente, se houver (usado para rastreabilidade de erros). */
    public static SupportContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
