package com.designart.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Assinatura corrente de um tenant (uma por tenant). Estado COMERCIAL apenas: nunca altera nem é
 * alterada automaticamente por {@code Tenant.status}. Entidade do PLANO DE CONTROLE: só é acessada por
 * {@code /api/admin/**} e pelo EntitlementService, sempre com tenantId explícito de origem confiável.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false, unique = true)
    private Long tenantId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "grace_period_end")
    private LocalDateTime gracePeriodEnd;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
