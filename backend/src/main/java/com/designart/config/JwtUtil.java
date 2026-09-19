package com.designart.config;

import com.designart.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * Emissão e validação de JWT.
 * <p>
 * Claims: {@code sub} = ID do usuário (nunca e-mail/username), {@code tv} =
 * token_version do usuário no momento da emissão, {@code role} (informativo:
 * as authorities de cada requisição vêm SEMPRE do banco). Expira em 8h por
 * padrão. O token NÃO carrega tenant, e-mail, senha ou hash.
 */
@Component
public class JwtUtil {

    // Sem valor padrao no código: cada perfil (dev/prod) define sua propria
    // origem em application-{profile}.properties. Em produção, a ausência de
    // JWT_SECRET faz a aplicação falhar ao subir, em vez de usar um segredo
    // previsível.
    @Value("${jwt.secret}")
    private String secret;

    // 8 horas (28_800_000 ms).
    @Value("${jwt.expiration:28800000}")
    private long jwtExpirationInMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        return generateToken(user, Duration.ofMillis(jwtExpirationInMs));
    }

    /** Sobrecarga com validade explícita (usada por testes para forjar tokens expirados). */
    public String generateToken(User user, Duration validity) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("tv", user.getTokenVersion())
                .claim("role", user.getRole() != null ? user.getRole().name() : null)
                .issuedAt(new Date(now))
                .expiration(new Date(now + validity.toMillis()))
                .signWith(getSigningKey())
                .compact();
    }

    /** Valida assinatura e expiração. Lança {@link JwtException}/{@link IllegalArgumentException} se inválido. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** ID do usuário (claim sub) ou {@code null} se ausente/malformado. */
    public static Long userId(Claims claims) {
        try {
            return claims.getSubject() == null ? null : Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** token_version do claim {@code tv} ou {@code null} se ausente. */
    public static Integer tokenVersion(Claims claims) {
        Object tv = claims.get("tv");
        return tv instanceof Number n ? n.intValue() : null;
    }
}
