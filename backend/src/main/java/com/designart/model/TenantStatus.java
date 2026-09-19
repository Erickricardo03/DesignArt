package com.designart.model;

/**
 * Situação OPERACIONAL do tenant (coluna {@code tenants.status}, catálogo fechado desde a V3).
 * ATIVO = pode usar o SaaS; SUSPENSO = acesso bloqueado (dados preservados); INATIVO = desativado.
 * Independente da situação comercial ({@code SubscriptionStatus}).
 */
public enum TenantStatus {
    ATIVO,
    SUSPENSO,
    INATIVO
}
