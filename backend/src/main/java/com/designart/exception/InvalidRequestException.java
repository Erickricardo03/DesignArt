package com.designart.exception;

/** Requisição inválida por regra de negócio (ex.: senha fora da política). Responde 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
