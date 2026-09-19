package com.designart.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Geração e hash de tokens de ação.
 * <ul>
 *   <li>32 bytes (256 bits) de {@link SecureRandom}, em Base64 URL-safe SEM padding (43 caracteres);</li>
 *   <li>no banco vai somente o SHA-256 (hex minúsculo, 64 caracteres). Como o token já tem alta
 *       entropia, um hash rápido é adequado (BCrypt seria desperdício) e permite busca por índice.</li>
 * </ul>
 */
public final class TokenCodec {

    public static final int ENTROPY_BYTES = 32;
    /** Comprimento do token Base64 URL-safe sem padding para 32 bytes. */
    public static final int TOKEN_LENGTH = 43;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenCodec() {
    }

    public static String generate() {
        byte[] bytes = new byte[ENTROPY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 (hex) do token puro; é o ÚNICO valor que chega ao banco. */
    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
