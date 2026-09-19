package com.designart.audit;

/** Tipo de ator: usuário autenticado, o próprio sistema, ou anônimo (ex.: falha de login). */
public enum AuditActorType {
    USER,
    SYSTEM,
    ANONYMOUS
}
