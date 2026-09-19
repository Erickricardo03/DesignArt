package com.designart.billing;

/**
 * Situação COMERCIAL da assinatura. Independente de {@code Tenant.status} (situação OPERACIONAL):
 * vencer/cancelar NÃO suspende o tenant, e suspender o tenant NÃO altera a assinatura.
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    CANCELED
}
