package com.designart.security;

/**
 * Capacidade de módulo DENTRO de um tenant. TENANT_ADMIN possui todas
 * implicitamente (nada é gravado para ele); USER só as atribuídas; SUPER_ADMIN
 * não usa permissões de tenant. Catálogo fechado: valores fora dele são
 * rejeitados (no Java e por CHECK no banco).
 */
public enum Permission {
    FINANCEIRO,
    EQUIPE,
    CONFIGURACOES;

    public String authority() {
        return "PERM_" + name();
    }
}
