package com.designart.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cobrança de um período. Valor, moeda, vencimento e fim da carência são um SNAPSHOT imutável da emissão
 * (colunas não atualizáveis). Só {@code status}/{@code paidAt}/{@code canceledAt} mudam, por
 * {@link BillingService}. Entidade do PLANO DE CONTROLE: acessada só por /api/admin/** e billing.
 */
@Entity
@Table(name = "billing_invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private Long subscriptionId;

    @Column(name = "reference_period", nullable = false, updatable = false)
    private LocalDate referencePeriod;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "grace_ends_on", nullable = false, updatable = false)
    private LocalDate graceEndsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvoiceStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
