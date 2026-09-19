package com.designart.exception;

/**
 * Operação proibida para o usuário autenticado por regra de negócio (ex.:
 * escalada de role, remover o último administrador). Responde 403.
 */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
