package com.designart.billing;

/** Efeito explícito de um override por tenant: concede ou remove uma feature, acima do plano. */
public enum OverrideEffect {
    ALLOW,
    DENY
}
