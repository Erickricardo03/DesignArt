package com.designart.audit;

/**
 * Categorias FECHADAS de motivo (nunca texto livre). Em LOGIN_FAILURE o motivo é
 * interno à auditoria — a resposta HTTP ao cliente continua sendo sempre genérica.
 */
public enum AuditReason {
    // LOGIN_FAILURE
    UNKNOWN_ACCOUNT,
    BAD_CREDENTIALS,
    ACCESS_DENIED,
    ACCOUNT_LOCKED,
    // SESSIONS_REVOKED
    EMAIL_CHANGED,
    ROLE_CHANGED,
    DEACTIVATED,
    PASSWORD_CHANGED,
    // *_EMAIL_FAILED
    EMAIL_DELIVERY_FAILED
}
