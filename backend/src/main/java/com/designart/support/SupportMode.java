package com.designart.support;

/** Modo da Support Session. Toda sessão nasce READ_ONLY; INTERVENTION exige elevação explícita e temporária. */
public enum SupportMode {
    READ_ONLY,
    INTERVENTION
}
