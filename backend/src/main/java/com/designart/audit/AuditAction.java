package com.designart.audit;

/**
 * CATÁLOGO de ações auditáveis. Só entram aqui ações que JÁ existem no sistema.
 * Nenhuma ação carrega segredo: o detalhe vai em {@link AuditMetadata} (enums/booleanos/contagens).
 */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    /** Conta bloqueada temporariamente após falhas consecutivas de login. */
    ACCOUNT_LOCKED,
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    USER_ACTIVATED,
    USER_DEACTIVATED,
    USER_ROLE_CHANGED,
    USER_PERMISSIONS_CHANGED,
    SESSIONS_REVOKED,
    /** Recuperação de senha solicitada para uma conta REAL e elegível (nunca para conta inexistente). */
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    /** Falha na entrega do e-mail de recuperação; o token foi revogado. */
    PASSWORD_RESET_EMAIL_FAILED,
    INVITE_CREATED,
    INVITE_RESENT,
    INVITE_ACCEPTED,
    /** Falha na entrega do e-mail de convite; o token foi revogado (o administrador pode reenviar). */
    INVITE_EMAIL_FAILED,
    // --- Nexus Control Center (SUPER_ADMIN, /api/admin/**) ---
    /** Primeiro SUPER_ADMIN criado pelo bootstrap (habilitado por configuração; nunca registra credencial). */
    SUPER_ADMIN_BOOTSTRAPPED,
    TENANT_CREATED,
    TENANT_SUSPENDED,
    TENANT_REACTIVATED,
    PLAN_CREATED,
    PLAN_UPDATED,
    PLAN_FEATURES_CHANGED,
    SUBSCRIPTION_CREATED,
    SUBSCRIPTION_CHANGED,
    TENANT_FEATURE_OVERRIDE_CHANGED,
    TENANT_BRANDING_CHANGED,
    // --- Financeiro SaaS (Fase 4.4.2). Nunca carregam valores monetários, apenas ids/estados. ---
    INVOICE_CREATED,
    INVOICE_MARKED_PAID,
    INVOICE_CANCELED,
    PAYMENT_RECORDED,
    TENANT_SUSPENDED_NON_PAYMENT
}
