package com.designart.audit;

import java.util.regex.Pattern;

/**
 * Saneamento de texto NÃO CONFIÁVEL antes de gravar na trilha.
 * <p>
 * O User-Agent vem do cliente e pode conter qualquer coisa: é tratado como texto
 * inerte. Aqui: caracteres de controle/formatação (que permitiriam forjar linhas
 * de log — CR/LF — ou confundir visualmente) viram espaço; padrões que parecem
 * credenciais (JWT, {@code Bearer ...}, hash BCrypt) são substituídos por
 * {@code [REDACTED]}; e o tamanho é limitado. NÃO se escapa HTML aqui: o valor
 * armazenado é texto puro e qualquer tela futura deve escapá-lo ao exibir
 * (nunca renderizar como HTML).
 */
public final class AuditSanitizer {

    public static final int MAX_USER_AGENT = 200;
    public static final int MAX_EMAIL = 254;
    static final String REDACTED = "[REDACTED]";

    private static final Pattern CONTROLES = Pattern.compile("[\\p{Cc}\\p{Cf}\\u2028\\u2029]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}(\\.[A-Za-z0-9_-]*)?");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{6,}");
    private static final Pattern BCRYPT = Pattern.compile("\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{20,}");

    private AuditSanitizer() {
    }

    public static String userAgent(String raw) {
        if (raw == null) {
            return null;
        }
        String s = CONTROLES.matcher(raw).replaceAll(" ");
        // Bearer/Basic primeiro: a credencial inteira (incl. a palavra) some antes de redigir JWTs soltos.
        s = BEARER.matcher(s).replaceAll(REDACTED);
        s = JWT.matcher(s).replaceAll(REDACTED);
        s = BCRYPT.matcher(s).replaceAll(REDACTED);
        s = s.trim();
        if (s.isEmpty()) {
            return null;
        }
        return truncar(s, MAX_USER_AGENT);
    }

    /** Snapshot de e-mail (sempre vem do banco); só limita tamanho e remove controles. */
    static String email(String raw) {
        if (raw == null) {
            return null;
        }
        String s = CONTROLES.matcher(raw).replaceAll("").trim();
        return s.isEmpty() ? null : truncar(s, MAX_EMAIL);
    }

    /** Trunca por code points (nunca corta um par substituto ao meio). */
    private static String truncar(String s, int maxCodePoints) {
        if (s.codePointCount(0, s.length()) <= maxCodePoints) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxCodePoints));
    }
}
