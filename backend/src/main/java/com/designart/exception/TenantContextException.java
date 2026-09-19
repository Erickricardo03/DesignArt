package com.designart.exception;

/**
 * Lançada quando uma operação tenant-scoped é tentada sem um tenant
 * resolvido para o usuário autenticado da requisição.
 */
public class TenantContextException extends RuntimeException {
    public TenantContextException(String message) {
        super(message);
    }
}
