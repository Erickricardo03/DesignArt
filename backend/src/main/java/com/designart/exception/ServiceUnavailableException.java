package com.designart.exception;

/** Funcionalidade indisponível no momento (ex.: envio de e-mail não configurado). Responde 503. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
