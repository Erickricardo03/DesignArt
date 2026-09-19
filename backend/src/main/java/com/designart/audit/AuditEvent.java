package com.designart.audit;

import com.designart.security.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Evento de auditoria — APPEND-ONLY.
 * <ul>
 *   <li>sem setters; todas as colunas {@code updatable=false};</li>
 *   <li>construtor e fábrica NÃO públicos: só o {@link AuditService} cria eventos,
 *       então ninguém monta registros de auditoria manualmente;</li>
 *   <li>{@code @PreUpdate}/{@code @PreRemove} lançam exceção (o repositório também
 *       não expõe update/delete) e, no PostgreSQL, triggers bloqueiam
 *       UPDATE/DELETE/TRUNCATE (V4).</li>
 * </ul>
 * Sem FKs e sem propriedade {@code tenantId}: não é uma entidade TENANT-SCOPED
 * comum (tenant alvo é dado de auditoria, nullable, com snapshot).
 */
@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private AuditActorType actorType;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_email", updatable = false, length = 254)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", updatable = false, length = 20)
    private Role actorRole;

    @Column(name = "actor_tenant_id", updatable = false)
    private Long actorTenantId;

    @Column(name = "target_tenant_id", updatable = false)
    private Long targetTenantId;

    @Column(name = "target_user_id", updatable = false)
    private Long targetUserId;

    @Column(name = "target_email", updatable = false, length = 254)
    private String targetEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 60)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", updatable = false, length = 40)
    private AuditEntityType entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 10)
    private AuditOutcome outcome;

    @Column(name = "metadata", updatable = false, length = AuditMetadata.MAX_LENGTH)
    private String metadata;

    @Column(name = "request_ip", updatable = false, length = 45)
    private String requestIp;

    @Column(name = "user_agent", updatable = false, length = 200)
    private String userAgent;

    /** Único ponto de criação (mesmo pacote que o AuditService). Recebe valores já saneados. */
    static AuditEvent create(LocalDateTime occurredAt, AuditAction action, AuditOutcome outcome,
                             AuditActor actor, AuditTarget target, String metadataJson,
                             String requestIp, String userAgent) {
        AuditEvent e = new AuditEvent();
        e.occurredAt = occurredAt;
        e.action = action;
        e.outcome = outcome;
        e.actorType = actor.type();
        e.actorUserId = actor.userId();
        e.actorEmail = AuditSanitizer.email(actor.email());
        e.actorRole = actor.role();
        e.actorTenantId = actor.tenantId();
        e.targetTenantId = target.tenantId();
        e.targetUserId = target.userId();
        e.targetEmail = AuditSanitizer.email(target.email());
        e.entityType = target.entityType();
        e.entityId = target.entityId();
        e.metadata = metadataJson;
        e.requestIp = requestIp;
        e.userAgent = userAgent;
        e.validar();
        return e;
    }

    private void validar() {
        boolean usuario = actorType == AuditActorType.USER;
        if (usuario != (actorUserId != null)) {
            throw new IllegalStateException("Ator USER exige actorUserId (e os demais tipos nunca o possuem).");
        }
        if (usuario && (actorEmail == null || actorRole == null)) {
            throw new IllegalStateException("Ator USER exige snapshots de e-mail e role.");
        }
        if (metadata != null && metadata.length() > AuditMetadata.MAX_LENGTH) {
            throw new IllegalStateException("Metadata acima do limite.");
        }
    }

    @PreUpdate
    void bloquearAtualizacao() {
        throw new IllegalStateException("audit_events é append-only: atualização não permitida.");
    }

    @PreRemove
    void bloquearRemocao() {
        throw new IllegalStateException("audit_events é append-only: remoção não permitida.");
    }
}
