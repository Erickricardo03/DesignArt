package com.designart.observability;

/**
 * Categoria de um erro de aplicação. Hoje só são CAPTURADOS erros de servidor (5xx): INTEGRATION, DATABASE e
 * INTERNAL. As demais existem no catálogo (e no CHECK do banco) para evolução, mas 4xx de validação/negócio/
 * autenticação NÃO viram incidente (evita ruído e armazenamento de entrada do usuário).
 */
public enum ErrorCategory {
    VALIDATION,
    AUTHENTICATION,
    AUTHORIZATION,
    BUSINESS,
    INTEGRATION,
    DATABASE,
    INTERNAL
}
