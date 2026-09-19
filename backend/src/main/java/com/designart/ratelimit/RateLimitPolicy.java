package com.designart.ratelimit;

/**
 * Políticas de limitação (janela deslizante). Cada uma tem padrão seguro e pode ser ajustada por
 * propriedade: {@code nexus.ratelimit.<nome>.max} e {@code nexus.ratelimit.<nome>.window-seconds}.
 * <p>
 * Desenho anti-DoS: nenhuma política bloqueia uma IDENTIDADE (e-mail) para o mundo inteiro. Limites por
 * identidade são sempre combinados com o IP ({@link #LOGIN_IP_IDENTITY}) ou aplicados de forma SILENCIOSA
 * (forgot-password), para que um atacante não consiga impedir a vítima de entrar ou de recuperar a conta.
 */
public enum RateLimitPolicy {
    /** Todas as tentativas de login vindas de um IP. */
    LOGIN_IP("login-ip", 60, 900),
    /** Tentativas de login para a MESMA identidade a partir do MESMO IP (identidade normalizada, exista ou não). */
    LOGIN_IP_IDENTITY("login-ip-identity", 10, 900),
    /** Pedidos de recuperação de senha por IP (excedido => 429; não revela nada sobre contas). */
    FORGOT_IP("forgot-ip", 10, 3600),
    /** Pedidos por identidade normalizada (excedido => descarte SILENCIOSO, mesma resposta 202). */
    FORGOT_IDENTITY("forgot-identity", 3, 3600),
    RESET_IP("reset-ip", 15, 900),
    ACCEPT_INVITE_IP("accept-invite-ip", 15, 900),
    /** Convites criados por um administrador (abuso de SMTP). */
    INVITE_CREATE_ACTOR("invite-create-actor", 30, 3600),
    /** Reenvios de convite por um administrador (abuso de SMTP). */
    INVITE_RESEND_ACTOR("invite-resend-actor", 10, 3600);

    private final String propertyName;
    private final int defaultMax;
    private final int defaultWindowSeconds;

    RateLimitPolicy(String propertyName, int defaultMax, int defaultWindowSeconds) {
        this.propertyName = propertyName;
        this.defaultMax = defaultMax;
        this.defaultWindowSeconds = defaultWindowSeconds;
    }

    public String propertyName() {
        return propertyName;
    }

    public int defaultMax() {
        return defaultMax;
    }

    public int defaultWindowSeconds() {
        return defaultWindowSeconds;
    }
}
