package com.designart.exception;

/** Conflito com o estado atual (ex.: e-mail já cadastrado). Responde 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
