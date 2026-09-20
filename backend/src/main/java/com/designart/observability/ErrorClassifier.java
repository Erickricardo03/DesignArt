package com.designart.observability;

import com.designart.exception.ServiceUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Classificação SEGURA de erros: nada aqui lê mensagem de exceção, corpo, parâmetros ou cabeçalhos. A mensagem
 * exibida ({@code safeMessage}) é uma constante por categoria.
 *
 * <h3>Fingerprint (agrupamento)</h3>
 * {@code SHA-256( categoria | código | método | caminho normalizado | classe da exceção )}, em hexadecimal.
 * O caminho é o PADRÃO da rota (ex.: {@code /api/admin/tenants/{id}}), sem ids nem query string. Só entram
 * informações estruturais estáveis: nunca mensagem livre, token, payload ou dado pessoal. Erros repetidos do mesmo
 * tenant com o mesmo fingerprint, enquanto não resolvidos, incrementam {@code occurrence_count} em vez de gerar
 * novas linhas.
 */
public final class ErrorClassifier {

    private static final Pattern SAFE_CLASS = Pattern.compile("^[A-Za-z0-9_.$]{1,100}$");
    private static final Pattern SAFE_PATH = Pattern.compile("^/[^?#\\s\\p{Cntrl}]*$");
    private static final Pattern ID_SEGMENT = Pattern.compile(
            "^(\\d+|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{16,}|[A-Za-z0-9_-]{24,})$");

    public record Classification(ErrorCategory category, String code, String safeMessage, String exceptionClass) {
    }

    private ErrorClassifier() {
    }

    /** {@code ex} pode ser nulo (5xx tratado sem exceção visível). */
    public static Classification classify(Throwable ex, int status) {
        String cls = safeClass(ex);
        if (ex instanceof DataAccessException || ex instanceof java.sql.SQLException
                || ex instanceof jakarta.persistence.PersistenceException) {
            return new Classification(ErrorCategory.DATABASE, "DATABASE_ERROR", "Falha ao acessar o banco de dados.", cls);
        }
        if (ex instanceof ServiceUnavailableException || ex instanceof MailException || ex instanceof java.net.SocketException
                || ex instanceof java.net.UnknownHostException || ex instanceof java.io.UncheckedIOException) {
            return new Classification(ErrorCategory.INTEGRATION, "INTEGRATION_UNAVAILABLE", "Falha em integração externa.", cls);
        }
        if (ex == null && status == 503) {
            return new Classification(ErrorCategory.INTEGRATION, "INTEGRATION_UNAVAILABLE", "Falha em integração externa.", null);
        }
        return new Classification(ErrorCategory.INTERNAL, "INTERNAL_ERROR", "Erro interno inesperado.", cls);
    }

    /** Nome da classe (nunca a mensagem) se for seguro; caso contrário nulo. */
    static String safeClass(Throwable ex) {
        if (ex == null) {
            return null;
        }
        String n = ex.getClass().getName();
        return SAFE_CLASS.matcher(n).matches() ? n : null;
    }

    /** Caminho normalizado: o padrão da rota; sem ele, o URI com segmentos de id trocados por {@code {id}}. Sem query. */
    public static String normalizePath(String routePattern, String requestUri) {
        String base = routePattern != null && !routePattern.isBlank() ? routePattern : requestUri;
        if (base == null) {
            return "/unknown";
        }
        int q = base.indexOf('?');
        if (q >= 0) {
            base = base.substring(0, q);
        }
        if (routePattern == null || routePattern.isBlank()) {
            String[] parts = base.split("/", -1);
            for (int i = 0; i < parts.length; i++) {
                if (ID_SEGMENT.matcher(parts[i]).matches()) {
                    parts[i] = "{id}";
                }
            }
            base = String.join("/", parts);
        }
        if (base.length() > 200) {
            base = base.substring(0, 200);
        }
        return SAFE_PATH.matcher(base).matches() ? base : "/unknown";
    }

    public static String fingerprint(ErrorCategory category, String code, String method, String path, String exceptionClass) {
        String basis = category.name() + "|" + code + "|" + method + "|" + path + "|" + (exceptionClass == null ? "" : exceptionClass);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
