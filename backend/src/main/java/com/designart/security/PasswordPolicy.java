package com.designart.security;

import com.designart.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Política ÚNICA de senha (criação/alteração; reutilizada no futuro por
 * convite e reset):
 * <ul>
 *   <li>mínimo de 10 caracteres (code points);</li>
 *   <li>máximo de 72 BYTES em UTF-8 — limite real do BCrypt, que trunca em
 *       silêncio acima disso; contar só {@code String.length()} não basta;</li>
 *   <li>sem o caractere NUL (o BCrypt o trata como fim da senha);</li>
 *   <li>não pode ser igual ao e-mail normalizado.</li>
 * </ul>
 * Nunca registra a senha em log nem a inclui na mensagem de erro.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    public void validate(String rawPassword, String normalizedEmail) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidRequestException("Informe uma senha.");
        }
        if (rawPassword.codePointCount(0, rawPassword.length()) < MIN_LENGTH) {
            throw new InvalidRequestException("A senha deve ter no mínimo " + MIN_LENGTH + " caracteres.");
        }
        if (exceedsMaxBytes(rawPassword)) {
            throw new InvalidRequestException("A senha deve ter no máximo " + MAX_BYTES
                    + " bytes (UTF-8); caracteres acentuados ocupam mais de um byte.");
        }
        if (rawPassword.indexOf('\u0000') >= 0) {
            throw new InvalidRequestException("A senha contém um caractere inválido.");
        }
        if (normalizedEmail != null && rawPassword.trim().toLowerCase(java.util.Locale.ROOT).equals(normalizedEmail)) {
            throw new InvalidRequestException("A senha não pode ser igual ao e-mail.");
        }
    }

    /** {@code true} se a senha ocupa mais de 72 bytes em UTF-8 (nunca pode ter sido criada pela política). */
    public static boolean exceedsMaxBytes(String rawPassword) {
        return rawPassword != null && rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
