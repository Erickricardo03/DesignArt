package com.designart.security;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalização e validação central de e-mail (identidade de login).
 * O e-mail é SEMPRE armazenado e consultado normalizado (trim + lowercase com
 * {@link Locale#ROOT}), então a comparação case-insensitive vira um UNIQUE simples.
 */
public final class EmailAddress {

    private static final int MAX_LENGTH = 254;
    private static final int MAX_LOCAL_LENGTH = 64;
    // Estrutura mínima segura: local@dominio.tld, sem espaços nem múltiplos "@".
    private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private EmailAddress() {
    }

    /** Retorna o e-mail normalizado, ou {@code null} se ausente/vazio/inválido. */
    public static String normalizeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > MAX_LENGTH || !FORMATO.matcher(email).matches()) {
            return null;
        }
        if (email.indexOf('@') > MAX_LOCAL_LENGTH) {
            return null;
        }
        return email;
    }

    /** Somente trim + lowercase, sem validar o formato (usado ao normalizar valores já persistidos). */
    public static String normalizeLoose(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }
}
