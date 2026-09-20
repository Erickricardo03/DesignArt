package com.designart.support;

import com.designart.exception.InvalidRequestException;

import java.util.regex.Pattern;

/** Validações de entrada do Modo Suporte (falham com 400, sem ecoar o valor recebido). */
public final class SupportRules {

    public static final int REASON_MIN = 10;
    public static final int REASON_MAX = 300;

    /** Cara de segredo: JWT, Bearer/Basic, "senha=..." / "token: ..." etc. O motivo nunca deve carregá-los. */
    private static final Pattern SEGREDO = Pattern.compile(
            "(?i)(eyJ[A-Za-z0-9_-]{10,}|\\b(bearer|basic)\\s+\\S+|\\b(senha|password|passwd|secret|token|apikey|api_key)\\s*[:=])");

    private SupportRules() {
    }

    /** Motivo obrigatório: 10..300 caracteres, sem controles e sem padrão de segredo. Devolve o texto normalizado. */
    public static String reason(String raw) {
        String r = raw == null ? null : raw.strip();
        if (r == null || r.length() < REASON_MIN || r.length() > REASON_MAX
                || r.chars().anyMatch(Character::isISOControl) || SEGREDO.matcher(r).find()) {
            throw new InvalidRequestException("Informe o motivo do suporte (" + REASON_MIN + " a " + REASON_MAX
                    + " caracteres, sem senhas, tokens ou caracteres de controle).");
        }
        return r;
    }

    /** E-mail mascarado para exibição em suporte (minimização de dados): {@code j***@dominio.com}. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
