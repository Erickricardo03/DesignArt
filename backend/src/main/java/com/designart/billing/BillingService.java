package com.designart.billing;

import com.designart.audit.*;
import com.designart.exception.ConflictException;
import com.designart.exception.InvalidRequestException;
import com.designart.exception.ResourceNotFoundException;
import com.designart.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Serviço CENTRAL das mensalidades (plano de controle, SUPER_ADMIN). Único ponto que emite, vence, paga e
 * cancela cobranças; cada mutação audita na MESMA transação (sem "operação sem auditoria").
 *
 * <p>Independência de conceitos: nada aqui altera {@code Tenant.status}, {@code Subscription.status},
 * features/entitlements ou branding. Uma cobrança vencida NÃO suspende o tenant.
 *
 * <p>Tempo: sempre o {@link Clock} injetado (UTC). "Hoje" = {@code LocalDate.now(clock)}.
 * Datas/regras de vencimento e carência: {@link BillingRules}.
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final AuditService auditService;
    private final AuditActors auditActors;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /** {@code created=false}: já existia cobrança ativa do período (geração idempotente; nada foi criado). */
    public record GenerationResult(Invoice invoice, boolean created) {
    }

    // ------------------------------------------------------------------ geração
    /**
     * Gera a mensalidade da competência ({@code null} = mês corrente UTC). IDEMPOTENTE: se já existe cobrança
     * NÃO cancelada do mesmo período para a assinatura, devolve a existente sem criar nada nem auditar.
     * O valor, a moeda, o vencimento e a carência são copiados do contrato VIGENTE neste instante e ficam
     * imutáveis (mudar o contrato/plano depois não retroage).
     */
    public GenerationResult generate(Long tenantId, LocalDate requestedPeriod) {
        if (tenantId == null || !tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant não encontrado.");
        }
        Subscription sub = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Este tenant ainda não possui assinatura."));
        if (sub.getStatus() == SubscriptionStatus.CANCELED) {
            throw new ConflictException("A assinatura está cancelada: não é possível gerar cobrança.");
        }
        if (sub.getContractedAmount() == null) {
            throw new ConflictException("A assinatura não possui valor contratado: defina os termos de cobrança antes.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate current = BillingRules.periodStart(today);
        LocalDate period = requestedPeriod == null ? current : requestedPeriod;
        if (period.getDayOfMonth() != 1) {
            throw new InvalidRequestException("A competência deve ser o primeiro dia do mês (AAAA-MM-01).");
        }
        if (period.isAfter(current)) {
            throw new InvalidRequestException("Não é possível gerar cobrança de uma competência futura.");
        }
        if (period.isBefore(BillingRules.periodStart(sub.getStartedAt().toLocalDate()))) {
            throw new InvalidRequestException("A competência é anterior ao início da assinatura.");
        }

        var existing = invoiceRepository.findBySubscriptionIdAndReferencePeriodAndStatusNot(
                sub.getId(), period, InvoiceStatus.CANCELED);
        if (existing.isPresent()) {
            return new GenerationResult(existing.get(), false);
        }
        try {
            Invoice created = new TransactionTemplate(transactionManager).execute(status -> {
                // Serializa emissões concorrentes da mesma assinatura e revalida DENTRO da transação: a unicidade
                // não depende só do índice parcial do PostgreSQL (o H2 de teste não o possui).
                subscriptionRepository.findByIdForUpdate(sub.getId());
                if (invoiceRepository.findBySubscriptionIdAndReferencePeriodAndStatusNot(sub.getId(), period, InvoiceStatus.CANCELED).isPresent()) {
                    return null;
                }
                Invoice i = new Invoice();
                i.setTenantId(tenantId);
                i.setSubscriptionId(sub.getId());
                i.setReferencePeriod(period);
                i.setAmount(sub.getContractedAmount());
                i.setCurrency(sub.getCurrency());
                i.setDueDate(BillingRules.dueDate(period, sub.getBillingDay()));
                i.setGraceEndsOn(i.getDueDate().plusDays(sub.getGraceDays()));
                i.setStatus(BillingRules.isPastDue(i.getDueDate(), today) ? InvoiceStatus.OVERDUE : InvoiceStatus.OPEN);
                LocalDateTime now = LocalDateTime.now(clock);
                i.setCreatedAt(now);
                i.setUpdatedAt(now);
                i = invoiceRepository.saveAndFlush(i);
                auditService.success(AuditAction.INVOICE_CREATED, auditActors.current(),
                        AuditTarget.entity(tenantId, AuditEntityType.INVOICE, i.getId()),
                        AuditMetadata.builder().invoiceStatusChange(null, i.getStatus()).build());
                return i;
            });
            if (created == null) { // outra requisição emitiu entre a checagem rápida e o lock
                return new GenerationResult(invoiceRepository.findBySubscriptionIdAndReferencePeriodAndStatusNot(
                        sub.getId(), period, InvoiceStatus.CANCELED).orElseThrow(), false);
            }
            return new GenerationResult(created, true);
        } catch (DataIntegrityViolationException e) {
            // Corrida: outra requisição emitiu o mesmo período. O índice único parcial garante uma só.
            return invoiceRepository.findBySubscriptionIdAndReferencePeriodAndStatusNot(sub.getId(), period, InvoiceStatus.CANCELED)
                    .map(i -> new GenerationResult(i, false))
                    .orElseThrow(() -> new ConflictException("Não foi possível gerar a cobrança."));
        }
    }

    // ------------------------------------------------------------------ vencimento
    /**
     * OPEN -> OVERDUE para toda cobrança com vencimento anterior a hoje (UTC). Idempotente e derivado do
     * relógio do servidor (nunca do frontend). Não audita: é transição derivada, sem ator. Não toca em tenant
     * nem em assinatura. Devolve quantas cobranças mudaram.
     */
    @Transactional
    public int refreshOverdue() {
        return invoiceRepository.markOverdue(LocalDate.now(clock), LocalDateTime.now(clock));
    }

    // ------------------------------------------------------------------ pagamento manual
    /**
     * Registra um pagamento MANUAL integral e marca a cobrança como PAID. O tenant/valor vêm da cobrança
     * persistida (nunca do corpo). Recusa: cobrança paga (duplicidade), cancelada, valor diferente, data futura.
     */
    @Transactional
    public Invoice pay(Long invoiceId, BigDecimal amount, LocalDateTime occurredAt, String externalRef) {
        Invoice invoice = lock(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Esta cobrança já foi paga.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELED) {
            throw new ConflictException("Uma cobrança cancelada não pode ser paga.");
        }
        if (amount == null || amount.compareTo(invoice.getAmount()) != 0) {
            throw new InvalidRequestException("O valor informado deve ser igual ao valor da cobrança (pagamento parcial não é suportado).");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime when = occurredAt == null ? now : occurredAt;
        if (when.isAfter(now)) {
            throw new InvalidRequestException("A data do pagamento não pode ser futura.");
        }
        AuditActor actor = auditActors.current();

        InvoicePayment p = new InvoicePayment();
        p.setInvoiceId(invoice.getId());
        p.setTenantId(invoice.getTenantId());
        p.setKind(PaymentKind.PAYMENT);
        p.setMethod(PaymentMethod.MANUAL);
        p.setAmount(invoice.getAmount());
        p.setCurrency(invoice.getCurrency());
        p.setOccurredAt(when);
        p.setRegisteredByUserId(actor.userId());
        p.setExternalRef(externalRef);
        p.setCreatedAt(now);
        p = paymentRepository.saveAndFlush(p);

        InvoiceStatus before = invoice.getStatus();
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(when);
        invoice.setUpdatedAt(now);
        invoice = invoiceRepository.saveAndFlush(invoice);

        auditService.success(AuditAction.PAYMENT_RECORDED, actor,
                AuditTarget.entity(invoice.getTenantId(), AuditEntityType.PAYMENT, p.getId()),
                AuditMetadata.builder().paymentMethod(PaymentMethod.MANUAL).build());
        auditService.success(AuditAction.INVOICE_MARKED_PAID, actor,
                AuditTarget.entity(invoice.getTenantId(), AuditEntityType.INVOICE, invoice.getId()),
                AuditMetadata.builder().invoiceStatusChange(before, InvoiceStatus.PAID).build());
        return invoice;
    }

    // ------------------------------------------------------------------ cancelamento
    /** Cancela cobrança devida (OPEN/OVERDUE). Cobrança paga ou já cancelada: 409. Não toca em tenant/assinatura. */
    @Transactional
    public Invoice cancel(Long invoiceId) {
        Invoice invoice = lock(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Uma cobrança paga não pode ser cancelada.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELED) {
            throw new ConflictException("Esta cobrança já está cancelada.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        InvoiceStatus before = invoice.getStatus();
        invoice.setStatus(InvoiceStatus.CANCELED);
        invoice.setCanceledAt(now);
        invoice.setUpdatedAt(now);
        invoice = invoiceRepository.saveAndFlush(invoice);
        auditService.success(AuditAction.INVOICE_CANCELED, auditActors.current(),
                AuditTarget.entity(invoice.getTenantId(), AuditEntityType.INVOICE, invoice.getId()),
                AuditMetadata.builder().invoiceStatusChange(before, InvoiceStatus.CANCELED).build());
        return invoice;
    }

    private Invoice lock(Long invoiceId) {
        if (invoiceId == null) {
            throw new ResourceNotFoundException("Cobrança não encontrada.");
        }
        return invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
    }
}
