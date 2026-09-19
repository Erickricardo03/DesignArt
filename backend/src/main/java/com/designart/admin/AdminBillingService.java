package com.designart.admin;

import com.designart.admin.dto.AdminDtos.*;
import com.designart.audit.*;
import com.designart.billing.*;
import com.designart.exception.InvalidRequestException;
import com.designart.exception.ResourceNotFoundException;
import com.designart.model.Tenant;
import com.designart.repository.TenantRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Consultas e termos do financeiro (SUPER_ADMIN). As mutações de cobrança ficam em {@link BillingService}.
 *
 * <p>Toda leitura começa por {@link BillingService#refreshOverdue()} (na mesma transação): OPEN vencida vira
 * OVERDUE no BACKEND, com base no relógio do servidor, antes de listar/agregar. Idempotente.
 * O tenant alvo sempre vem do path; listagens são paginadas e resolvem nomes de tenant em UMA consulta (sem N+1).
 */
@Service
@RequiredArgsConstructor
public class AdminBillingService {

    /** Visões operacionais de cobrança (todas derivadas das datas congeladas na emissão). */
    public enum View {ALL, UPCOMING, OVERDUE, IN_GRACE, BEYOND_GRACE}

    private static final int MAX_PAGE_SIZE = 100;

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository paymentRepository;
    private final BillingService billingService;
    private final AuditService auditService;
    private final AuditActors auditActors;
    private final Clock clock;

    // ------------------------------------------------------------------ cobranças
    @Transactional
    public PageDto<InvoiceDto> listInvoices(View view, Long tenantId, InvoiceStatus status, Integer days, int page, int size) {
        billingService.refreshOverdue();
        if (tenantId != null) {
            exigirTenant(tenantId);
        }
        LocalDate today = LocalDate.now(clock);
        int window = window(days);
        Specification<Invoice> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (tenantId != null) {
                p.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            switch (view) {
                case UPCOMING -> {
                    p.add(cb.equal(root.get("status"), InvoiceStatus.OPEN));
                    p.add(cb.greaterThanOrEqualTo(root.get("dueDate"), today));
                    p.add(cb.lessThanOrEqualTo(root.get("dueDate"), today.plusDays(window)));
                }
                case OVERDUE -> p.add(cb.equal(root.get("status"), InvoiceStatus.OVERDUE));
                case IN_GRACE -> {
                    p.add(cb.equal(root.get("status"), InvoiceStatus.OVERDUE));
                    p.add(cb.greaterThanOrEqualTo(root.get("graceEndsOn"), today));
                }
                case BEYOND_GRACE -> {
                    p.add(cb.equal(root.get("status"), InvoiceStatus.OVERDUE));
                    p.add(cb.lessThan(root.get("graceEndsOn"), today));
                }
                case ALL -> {
                }
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<Invoice> result = invoiceRepository.findAll(spec, pageable(page, size, Sort.by("dueDate", "id")));
        return new PageDto<>(toDtos(result.getContent(), today, window), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public InvoiceDto getInvoice(Long id) {
        billingService.refreshOverdue();
        Invoice i = invoiceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        return toDtos(List.of(i), LocalDate.now(clock), BillingRules.DEFAULT_UPCOMING_DAYS).get(0);
    }

    /** SEM @Transactional: BillingService controla a transação (trata a violação do índice único em corrida). */
    public InvoiceGenerationDto generate(Long tenantId, GenerateInvoiceRequest req) {
        var result = billingService.generate(tenantId, req == null ? null : req.referencePeriod());
        return new InvoiceGenerationDto(
                toDtos(List.of(result.invoice()), LocalDate.now(clock), BillingRules.DEFAULT_UPCOMING_DAYS).get(0),
                result.created());
    }

    public InvoiceDto pay(Long id, PayInvoiceRequest req) {
        if (req == null) {
            throw new InvalidRequestException("Corpo da requisição obrigatório.");
        }
        Invoice i = billingService.pay(id, AdminRules.money(req.amount()), req.occurredAt(), AdminRules.externalRef(req.externalRef()));
        return toDtos(List.of(i), LocalDate.now(clock), BillingRules.DEFAULT_UPCOMING_DAYS).get(0);
    }

    public InvoiceDto cancel(Long id) {
        Invoice i = billingService.cancel(id);
        return toDtos(List.of(i), LocalDate.now(clock), BillingRules.DEFAULT_UPCOMING_DAYS).get(0);
    }

    @Transactional
    public RefreshResultDto refresh() {
        return new RefreshResultDto(billingService.refreshOverdue());
    }

    // ------------------------------------------------------------------ pagamentos
    @Transactional(readOnly = true)
    public List<PaymentDto> paymentsOfInvoice(Long invoiceId) {
        if (!invoiceRepository.existsById(invoiceId)) {
            throw new ResourceNotFoundException("Cobrança não encontrada.");
        }
        return paymentRepository.findByInvoiceIdOrderByOccurredAtAscIdAsc(invoiceId).stream().map(AdminBillingService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PageDto<PaymentDto> listPayments(Long tenantId, int page, int size) {
        if (tenantId != null) {
            exigirTenant(tenantId);
        }
        Specification<InvoicePayment> spec = (root, q, cb) -> tenantId == null ? cb.conjunction() : cb.equal(root.get("tenantId"), tenantId);
        Page<InvoicePayment> r = paymentRepository.findAll(spec, pageable(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        return new PageDto<>(r.getContent().stream().map(AdminBillingService::toDto).toList(), r.getNumber(), r.getSize(), r.getTotalElements());
    }

    // ------------------------------------------------------------------ termos contratados
    @Transactional(readOnly = true)
    public BillingTermsDto getTerms(Long tenantId) {
        exigirTenant(tenantId);
        return terms(subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Este tenant ainda não possui assinatura.")));
    }

    /**
     * Define os termos comerciais CONTRATADOS deste tenant. Não altera cobranças já emitidas (snapshot) nem
     * o plano/status da assinatura. Auditoria registra só os NOMES dos campos alterados (nunca os valores).
     */
    @Transactional
    public BillingTermsDto setTerms(Long tenantId, BillingTermsRequest req) {
        exigirTenant(tenantId);
        if (req == null) {
            throw new InvalidRequestException("Corpo da requisição obrigatório.");
        }
        Subscription s = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Este tenant ainda não possui assinatura."));
        BigDecimal amount = req.contractedAmount() == null ? s.getContractedAmount() : AdminRules.money(req.contractedAmount());
        String currency = req.currency() == null ? s.getCurrency() : AdminRules.currency(req.currency());
        int day = req.billingDay() == null ? s.getBillingDay() : AdminRules.billingDay(req.billingDay());
        int grace = req.graceDays() == null ? s.getGraceDays() : AdminRules.graceDays(req.graceDays());

        Set<AuditField> changed = EnumSet.noneOf(AuditField.class);
        if (!Objects.equals(amount, s.getContractedAmount()) && !(amount != null && s.getContractedAmount() != null
                && amount.compareTo(s.getContractedAmount()) == 0)) {
            changed.add(AuditField.CONTRACTED_AMOUNT);
        }
        if (!currency.equals(s.getCurrency())) {
            changed.add(AuditField.CURRENCY);
        }
        if (day != s.getBillingDay()) {
            changed.add(AuditField.BILLING_DAY);
        }
        if (grace != s.getGraceDays()) {
            changed.add(AuditField.GRACE_DAYS);
        }
        if (!changed.isEmpty()) {
            s.setContractedAmount(amount);
            s.setCurrency(currency);
            s.setBillingDay(day);
            s.setGraceDays(grace);
            s.setUpdatedAt(LocalDateTime.now(clock));
            s = subscriptionRepository.saveAndFlush(s);
            auditService.success(AuditAction.SUBSCRIPTION_CHANGED, auditActors.current(),
                    AuditTarget.entity(tenantId, AuditEntityType.SUBSCRIPTION, s.getId()),
                    AuditMetadata.builder().subscriptionStatusChange(s.getStatus(), s.getStatus()).planChanged(false)
                            .fieldsChanged(changed).build());
        }
        return terms(s);
    }

    // ------------------------------------------------------------------ financeiro do tenant
    @Transactional
    public TenantBillingDto tenantBilling(Long tenantId) {
        billingService.refreshOverdue();
        Tenant t = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant não encontrado."));
        LocalDate today = LocalDate.now(clock);
        var sub = subscriptionRepository.findByTenantId(tenantId);
        Map<InvoiceStatus, Long> counts = new EnumMap<>(InvoiceStatus.class);
        Map<String, BigDecimal> outstanding = new TreeMap<>();
        Map<String, BigDecimal> overdue = new TreeMap<>();
        for (var row : invoiceRepository.totalsByStatusForTenant(tenantId)) {
            counts.merge(row.getStatus(), row.getQty(), Long::sum);
            if (row.getStatus().isUnpaid()) {
                outstanding.merge(row.getCurrency(), row.getTotal(), BigDecimal::add);
            }
            if (row.getStatus() == InvoiceStatus.OVERDUE) {
                overdue.merge(row.getCurrency(), row.getTotal(), BigDecimal::add);
            }
        }
        var recent = toDtos(invoiceRepository.findTop12ByTenantIdOrderByReferencePeriodDescIdDesc(tenantId), today,
                BillingRules.DEFAULT_UPCOMING_DAYS);
        return new TenantBillingDto(t.getId(), t.getName(), t.getStatus(),
                t.getSuspensionReason() == null ? null : t.getSuspensionReason().name(), t.getSuspendedAt(),
                sub.map(this::terms).orElse(null),
                counts.getOrDefault(InvoiceStatus.OPEN, 0L), counts.getOrDefault(InvoiceStatus.OVERDUE, 0L),
                invoiceRepository.countBeyondGraceForTenant(tenantId, today), counts.getOrDefault(InvoiceStatus.PAID, 0L),
                outstanding, overdue,
                paymentRepository.findFirstByTenantIdOrderByOccurredAtDescIdDesc(tenantId).map(InvoicePayment::getOccurredAt).orElse(null),
                recent);
    }

    // ------------------------------------------------------------------ dashboard (backend)
    /** Agregações fixas (~10 consultas, independentes do volume): sem N+1. Período: [from, to) em dias UTC. */
    @Transactional
    public BillingDashboardDto dashboard(LocalDate from, LocalDate to, Integer days) {
        billingService.refreshOverdue();
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from != null ? from : BillingRules.periodStart(today);
        LocalDate end = to != null ? to : start.plusMonths(1);
        if (!end.isAfter(start) || end.isAfter(start.plusDays(366))) {
            throw new InvalidRequestException("Período inválido: informe from < to, com no máximo 366 dias.");
        }
        int window = window(days);

        Map<String, Long> tenants = new HashMap<>();
        tenantRepository.countByStatusGrouped().forEach(r -> tenants.put((String) r[0], (Long) r[1]));
        Map<SubscriptionStatus, Long> subs = new EnumMap<>(SubscriptionStatus.class);
        subscriptionRepository.countByStatusGrouped().forEach(r -> subs.put((SubscriptionStatus) r[0], (Long) r[1]));

        Map<InvoiceStatus, Long> counts = new EnumMap<>(InvoiceStatus.class);
        Map<String, BigDecimal> pending = new TreeMap<>();
        Map<String, BigDecimal> overdue = new TreeMap<>();
        for (var r : invoiceRepository.totalsByStatus()) {
            counts.merge(r.getStatus(), r.getQty(), Long::sum);
            if (r.getStatus() == InvoiceStatus.OPEN) {
                pending.merge(r.getCurrency(), r.getTotal(), BigDecimal::add);
            } else if (r.getStatus() == InvoiceStatus.OVERDUE) {
                overdue.merge(r.getCurrency(), r.getTotal(), BigDecimal::add);
            }
        }
        long upcoming = 0;
        Map<String, BigDecimal> upcomingAmount = new TreeMap<>();
        for (var r : invoiceRepository.upcomingTotals(today, today.plusDays(window))) {
            upcoming += r.getQty();
            upcomingAmount.merge(r.getCurrency(), r.getTotal(), BigDecimal::add);
        }
        long inGrace = invoiceRepository.inGraceTotals(today).stream().mapToLong(InvoiceRepository.CurrencyTotal::getQty).sum();
        long beyond = invoiceRepository.beyondGraceTotals(today).stream().mapToLong(InvoiceRepository.CurrencyTotal::getQty).sum();
        Map<String, BigDecimal> received = new TreeMap<>();
        paymentRepository.receivedBetween(start.atStartOfDay(), end.atStartOfDay())
                .forEach(r -> received.merge(r.getCurrency(), r.getTotal(), BigDecimal::add));

        return new BillingDashboardDto(today, start, end, window,
                tenants.getOrDefault("ATIVO", 0L), tenants.getOrDefault("SUSPENSO", 0L), tenantRepository.countSuspendedForNonPayment(),
                subs.getOrDefault(SubscriptionStatus.ACTIVE, 0L), subs.getOrDefault(SubscriptionStatus.PAST_DUE, 0L),
                subs.getOrDefault(SubscriptionStatus.CANCELED, 0L),
                counts.getOrDefault(InvoiceStatus.OPEN, 0L), counts.getOrDefault(InvoiceStatus.OVERDUE, 0L), upcoming,
                inGrace, beyond, counts.getOrDefault(InvoiceStatus.PAID, 0L),
                received, pending, overdue, upcomingAmount);
    }

    // ------------------------------------------------------------------ internos
    private void exigirTenant(Long tenantId) {
        if (tenantId == null || !tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant não encontrado.");
        }
    }

    private static int window(Integer days) {
        if (days == null) {
            return BillingRules.DEFAULT_UPCOMING_DAYS;
        }
        if (days < 0 || days > BillingRules.MAX_UPCOMING_DAYS) {
            throw new InvalidRequestException("A janela de dias deve estar entre 0 e " + BillingRules.MAX_UPCOMING_DAYS + ".");
        }
        return days;
    }

    private static PageRequest pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }

    /** Converte em lote: UMA consulta para os nomes de tenant da página. */
    private List<InvoiceDto> toDtos(List<Invoice> invoices, LocalDate today, int window) {
        Set<Long> ids = invoices.stream().map(Invoice::getTenantId).collect(Collectors.toSet());
        Map<Long, String> names = tenantRepository.findAllById(ids).stream().collect(Collectors.toMap(Tenant::getId, Tenant::getName));
        return invoices.stream().map(i -> new InvoiceDto(i.getId(), i.getTenantId(), names.get(i.getTenantId()),
                i.getSubscriptionId(), i.getReferencePeriod(), i.getAmount(), i.getCurrency(), i.getDueDate(),
                i.getGraceEndsOn(), i.getStatus(), BillingRules.phase(i.getStatus(), i.getDueDate(), i.getGraceEndsOn(), today, window),
                i.getPaidAt(), i.getCanceledAt(), i.getCreatedAt())).toList();
    }

    private static PaymentDto toDto(InvoicePayment p) {
        return new PaymentDto(p.getId(), p.getInvoiceId(), p.getTenantId(), p.getKind(), p.getMethod(), p.getAmount(),
                p.getCurrency(), p.getOccurredAt(), p.getRegisteredByUserId(), p.getExternalRef());
    }

    private BillingTermsDto terms(Subscription s) {
        String planCode = planRepository.findById(s.getPlanId()).map(Plan::getCode).orElse(null);
        return new BillingTermsDto(s.getTenantId(), planCode, s.getStatus(), s.getContractedAmount(), s.getCurrency(),
                s.getBillingInterval(), s.getBillingDay(), s.getGraceDays());
    }
}
