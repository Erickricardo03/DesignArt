package com.designart.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Constrói os links dos e-mails SOMENTE a partir de {@code APP_PUBLIC_URL} (configuração). Nunca usa
 * Host, X-Forwarded-Host, Origin ou Referer (que o cliente controla) e não aceita redirecionamento
 * arbitrário: os caminhos são fixos.
 */
@Component
public class PublicLinks {

    static final String RESET_PATH = "/reset-password";
    static final String INVITE_PATH = "/accept-invite";

    private final String base;

    public PublicLinks(@Value("${app.public-url:}") String publicUrl) {
        this.base = normalizar(publicUrl);
    }

    public boolean isConfigured() {
        return !base.isEmpty();
    }

    public String resetPasswordUrl(String rawToken) {
        return montar(RESET_PATH, rawToken);
    }

    public String acceptInviteUrl(String rawToken) {
        return montar(INVITE_PATH, rawToken);
    }

    private String montar(String path, String rawToken) {
        if (base.isEmpty()) {
            throw new IllegalStateException("APP_PUBLIC_URL não configurada.");
        }
        return base + path + "?token=" + rawToken;
    }

    private static String normalizar(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(url.trim());
            boolean ok = uri.getScheme() != null && (uri.getScheme().equals("https") || uri.getScheme().equals("http"))
                    && uri.getHost() != null && uri.getQuery() == null && uri.getFragment() == null
                    && uri.getUserInfo() == null;
            if (!ok) {
                throw new IllegalStateException("APP_PUBLIC_URL inválida (esperado http(s)://host[:porta][/base]).");
            }
            String s = url.trim();
            while (s.endsWith("/")) {
                s = s.substring(0, s.length() - 1);
            }
            return s;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("APP_PUBLIC_URL inválida.");
        }
    }
}
