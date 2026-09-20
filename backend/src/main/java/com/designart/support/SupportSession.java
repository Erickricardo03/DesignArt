package com.designart.support;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sessão de suporte: SUPER_ADMIN real ({@code superAdminUserId}) -> tenant alvo. Não guarda segredo: a
 * referência externa é o {@code publicId} (UUID) e só vale para o próprio SUPER_ADMIN autenticado.
 * Entidade do plano de controle (só /api/admin/support/** e o serviço de suporte a acessam).
 */
@Entity
@Table(name = "support_sessions")
@Getter
@Setter
@NoArgsConstructor
public class SupportSession {

    /** Duração da sessão (fixa, sem renovação). O banco impõe teto rígido de 4 h. */
    public static final Duration SESSION_TTL = Duration.ofMinutes(30);
    /** Quanto tempo a elevação para INTERVENTION vale a partir de {@code elevatedAt}. */
    public static final Duration ELEVATION_TTL = Duration.ofMinutes(10);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, unique = true)
    private UUID publicId;

    @Column(name = "super_admin_user_id", nullable = false, updatable = false)
    private Long superAdminUserId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SupportMode mode;

    @Column(nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "elevated_at")
    private LocalDateTime elevatedAt;

    @Column(name = "elevation_reason", length = 300)
    private String elevationReason;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "request_ip", updatable = false, length = 64)
    private String requestIp;

    @Column(name = "user_agent", updatable = false, length = 200)
    private String userAgent;

    /** Ativa = não encerrada e ainda dentro da validade. Sessão encerrada/expirada nunca volta a ser usável. */
    public boolean isActive(LocalDateTime now) {
        return endedAt == null && now.isBefore(expiresAt);
    }

    /** Fim da elevação (nulo se nunca elevada). */
    public LocalDateTime elevationEndsAt() {
        return elevatedAt == null ? null : elevatedAt.plus(ELEVATION_TTL);
    }

    /** Modo EFETIVO agora: INTERVENTION só enquanto a elevação estiver vigente e a sessão ativa; senão READ_ONLY. */
    public SupportMode effectiveMode(LocalDateTime now) {
        return isActive(now) && elevatedAt != null && now.isBefore(elevationEndsAt())
                ? SupportMode.INTERVENTION : SupportMode.READ_ONLY;
    }
}
