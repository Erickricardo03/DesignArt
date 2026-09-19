package com.designart.billing;

/**
 * Códigos ESTÁVEIS do catálogo de features (dados de referência semeados na V6). São identificadores,
 * não regras: quem decide o que cada plano inclui é o banco (plan_features) via EntitlementService.
 * Não confundir com {@code Permission} (capacidade de um USUÁRIO dentro do que a empresa contratou).
 */
public final class FeatureCodes {
    public static final String FINANCEIRO = "FINANCEIRO";
    public static final String EQUIPE = "EQUIPE";
    public static final String RELATORIOS = "RELATORIOS";
    public static final String PORTFOLIO = "PORTFOLIO";
    public static final String CUSTOM_DOMAIN = "CUSTOM_DOMAIN";
    public static final String CUSTOM_BRANDING = "CUSTOM_BRANDING";
    public static final String CUSTOM_LOGO = "CUSTOM_LOGO";
    public static final String CUSTOM_COLORS = "CUSTOM_COLORS";
    public static final String MAX_USERS = "MAX_USERS";
    public static final String STORAGE_GB = "STORAGE_GB";

    private FeatureCodes() {
    }
}
