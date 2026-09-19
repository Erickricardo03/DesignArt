package com.designart.model;

/**
 * Por que um tenant está SUSPENSO (coluna {@code tenants.suspension_reason}). Só existe enquanto o status
 * for SUSPENSO. Distingue decisão administrativa de inadimplência; não altera assinatura nem cobranças.
 */
public enum SuspensionReason {
    MANUAL,
    NON_PAYMENT
}
