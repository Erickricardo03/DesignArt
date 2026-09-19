package com.designart.token;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Token de ação de uso único. Guarda SOMENTE o hash SHA-256; a posse deriva de user_id
 * (não há tenant_id aqui de propósito). Sem setters: o estado muda apenas pelos UPDATEs
 * condicionais atômicos do repositório (consumo/revogação).
 */
@Entity
@Table(name = "user_action_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserActionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 20)
    private ActionTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "requested_ip", updatable = false, length = 45)
    private String requestedIp;

    @Column(name = "created_by_user_id", updatable = false)
    private Long createdByUserId;

    static UserActionToken create(Long userId, ActionTokenPurpose purpose, String tokenHash,
                                  LocalDateTime createdAt, LocalDateTime expiresAt,
                                  String requestedIp, Long createdByUserId) {
        UserActionToken t = new UserActionToken();
        t.userId = userId;
        t.purpose = purpose;
        t.tokenHash = tokenHash;
        t.createdAt = createdAt;
        t.expiresAt = expiresAt;
        t.requestedIp = requestedIp;
        t.createdByUserId = createdByUserId;
        return t;
    }

    public boolean isActive(LocalDateTime now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }
}
