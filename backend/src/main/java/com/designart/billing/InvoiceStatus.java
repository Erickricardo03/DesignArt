package com.designart.billing;

/** Situação FINANCEIRA de uma cobrança (independente de Subscription.status e Tenant.status). */
public enum InvoiceStatus {
    OPEN,
    PAID,
    OVERDUE,
    CANCELED;

    /** Cobrança ainda devida (pode ser paga ou cancelada). */
    public boolean isUnpaid() {
        return this == OPEN || this == OVERDUE;
    }
}
