package com.designart.token;

import java.time.Duration;

/** Finalidade de um token de ação. Cada finalidade tem a própria validade. */
public enum ActionTokenPurpose {
    /** Recuperação de senha: 30 minutos. */
    PASSWORD_RESET(Duration.ofMinutes(30)),
    /** Convite de usuário: 72 horas. */
    INVITE(Duration.ofHours(72));

    private final Duration ttl;

    ActionTokenPurpose(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }
}
