package com.designart.audit;

/** Tipo da entidade afetada pelo evento. */
public enum AuditEntityType {
    USER,
    TENANT,
    PLAN,
    FEATURE,
    SUBSCRIPTION
}
