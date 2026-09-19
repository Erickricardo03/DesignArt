package com.designart.audit;

/**
 * Campos administrativos de usuário que podem constar como "alterados" (só o NOME do campo, nunca o valor).
 * A senha NÃO está aqui: administradores não definem senhas (convite/recuperação por e-mail).
 */
public enum AuditField {
    EMAIL,
    NOME,
    CARGO,
    DESCRIPTION,
    ACTIVE,
    PRIMARY_COLOR,
    SECONDARY_COLOR,
    ACCENT_COLOR,
    CONTRACTED_AMOUNT,
    CURRENCY,
    BILLING_DAY,
    GRACE_DAYS
}
