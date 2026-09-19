package com.designart.exception;

/** Limite de requisições excedido (429). Resposta genérica: nada sobre contas é revelado. */
public class TooManyRequestsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Muitas tentativas. Aguarde alguns minutos e tente novamente.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
