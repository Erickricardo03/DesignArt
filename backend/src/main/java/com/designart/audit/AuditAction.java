package com.designart.audit;

/**
 * CATÁLOGO de ações auditáveis. Só entram aqui ações que JÁ existem no sistema.
 * <p>
 * RESERVADAS para as próximas etapas (ainda NÃO existem; serão adicionadas junto com as
 * funcionalidades correspondentes, nunca antes): SUPER_ADMIN_BOOTSTRAPPED, TENANT_CREATED,
 * TENANT_SUSPENDED, TENANT_ACTIVATED, TENANT_STATUS_CHANGED.
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
    INVITE_EMAIL_FAILED
}
