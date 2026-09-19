package com.designart.admin;

import com.designart.exception.InvalidRequestException;

import java.util.regex.Pattern;

/** Validações de entrada compartilhadas pelos serviços administrativos (falham com 400, sem ecoar o valor). */
public final class AdminRules {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{0,48}[a-z0-9]$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern LOGO_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9/_.-]{0,254}$");

    private AdminRules() {
    }

    public static String code(String raw) {
        String c = raw == null ? null : raw.trim();
        if (c == null || !CODE.matcher(c).matches()) {
            throw new InvalidRequestException("Código inválido. Use letras maiúsculas, números e _ (2 a 50 caracteres, começando por letra).");
        }
        return c;
    }

    public static String name(String raw) {
        String n = raw == null ? null : raw.strip();
        if (n == null || n.length() < 2 || n.length() > 120 || hasControl(n)) {
            throw new InvalidRequestException("Informe um nome válido (2 a 120 caracteres).");
        }
        return n;
    }

    public static String optionalText(String raw, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.strip();
        if (t.length() > max || hasControl(t)) {
            throw new InvalidRequestException("Texto inválido ou longo demais (máximo " + max + " caracteres).");
        }
        return t;
    }

    public static String slug(String raw) {
        String s = raw == null ? null : raw.trim().toLowerCase();
        if (s == null || !SLUG.matcher(s).matches()) {
            throw new InvalidRequestException("Identificador (slug) inválido. Use letras minúsculas, números e hífen (2 a 50 caracteres).");
        }
        return s;
    }

    public static Long limit(Long value) {
        if (value != null && value < 0) {
            throw new InvalidRequestException("O limite não pode ser negativo.");
        }
        return value;
    }

    /**
     * Cor de branding: SOMENTE {@code #RRGGBB}. Qualquer outra coisa (nomes, rgb(), var(), url(), ;, CSS,
     * HTML, script) é recusada — o frontend só recebe um token de cor hexadecimal, nunca CSS arbitrário.
     * Nulo/vazio = restaurar o padrão. Normalizada para maiúsculas.
     */
    public static String color(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!COLOR.matcher(raw).matches()) {
            throw new InvalidRequestException("Cor inválida. Use o formato hexadecimal #RRGGBB.");
        }
        return raw.toUpperCase();
    }

    /** Chave OPACA de objeto (storage futuro): sem esquema, host, "..", espaços ou caracteres especiais. */
    public static boolean isSafeLogoKey(String key) {
        return key != null && LOGO_KEY.matcher(key).matches() && !key.contains("..") && !key.contains("//");
    }

    private static boolean hasControl(String s) {
        return s.chars().anyMatch(Character::isISOControl);
    }
}
