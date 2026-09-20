package com.designart.observability;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Incidente técnico AGRUPADO (um registro por tenant + fingerprint enquanto não resolvido; repetições incrementam
 * {@code occurrenceCount}). Guarda só metadados seguros: nunca body, query string, headers, cookies, tokens,
 * senha nem stack trace. Plano de controle (SUPER_ADMIN): acessado só por /api/admin/** e pelo suporte.
 */
@Entity
@Table(name = "application_error_events")
@Getter
@Setter
@NoArgsConstructor
public class ApplicationErrorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_occurred_at", nullable = false, updatable = false)
    private LocalDateTime firstOccurredAt;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "tenant_id", updatable = false)
    private Long tenantId;

    @Column(name = "support_session_id")
    private Long supportSessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "request_method", nullable = false, updatable = false, length = 10)
    private String requestMethod;

    @Column(name = "request_path", nullable = false, updatable = false, length = 200)
    private String requestPath;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "error_code", nullable = false, updatable = false, length = 60)
    private String errorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_category", nullable = false, updatable = false, length = 20)
    private ErrorCategory errorCategory;

    @Column(name = "exception_class", updatable = false, length = 100)
    private String exceptionClass;

    @Column(name = "safe_message", nullable = false, updatable = false, length = 200)
    private String safeMessage;

    @Column(nullable = false, updatable = false, length = 64)
    private String fingerprint;

    @Column(name = "occurrence_count", nullable = false)
    private long occurrenceCount = 1;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;
}
