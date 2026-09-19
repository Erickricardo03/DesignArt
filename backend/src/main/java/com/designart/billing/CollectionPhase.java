package com.designart.billing;

/**
 * Fase de cobrança derivada (nunca persistida) de uma cobrança em um dia. Ver {@link BillingRules#phase}.
 * NONE = paga/cancelada; CURRENT = em aberto e ainda distante do vencimento; UPCOMING = vence dentro da janela;
 * IN_GRACE = vencida mas dentro da carência; BEYOND_GRACE = vencida e fora da carência.
 */
public enum CollectionPhase {
    NONE,
    CURRENT,
    UPCOMING,
    IN_GRACE,
    BEYOND_GRACE
}
