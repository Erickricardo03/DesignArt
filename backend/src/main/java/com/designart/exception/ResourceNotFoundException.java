package com.designart.exception;

/**
 * Recurso não encontrado — usada tanto para "não existe" quanto para "existe,
 * mas pertence a outro tenant". De propósito: as duas situações retornam a
 * MESMA resposta (404 genérico), para não revelar a um tenant que um ID
 * pertence a outro tenant (mesmo princípio de não revelar existência de
 * usuário no login, ver Fase 1).
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
