package com.designart.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento financeiro de uma cobrança (histórico; nunca é atualizado nem apagado). Nunca guarda dado de cartão.
 * {@code externalRef} é uma referência opaca (ex.: id do comprovante), validada por formato.
 */
@Entity
@Table(name = "invoice_payments")
@Getter
@Setter
@NoArgsConstructor
public class InvoicePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private PaymentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private PaymentMethod method;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "registered_by_user_id", updatable = false)
    private Long registeredByUserId;

    @Column(name = "external_ref", updatable = false, length = 100)
    private String externalRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
