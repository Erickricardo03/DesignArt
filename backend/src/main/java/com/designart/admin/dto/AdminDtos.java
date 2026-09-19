package com.designart.admin.dto;

import com.designart.billing.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import com.designart.billing.FeatureKind;
import com.designart.billing.OverrideEffect;
import com.designart.billing.SubscriptionStatus;
import com.designart.dto.UserDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contratos de {@code /api/admin/**}. Nenhum DTO carrega senha, hash, token, JWT ou segredo de SMTP.
 * Campos desconhecidos enviados pelo cliente são ignorados; enums fora do catálogo dão 400.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ---- tenants ----
    public record TenantDto(Long id, String name, String slug, String status, String customDomain, LocalDateTime createdAt) {
    }

    /** O SUPER_ADMIN informa só o e-mail (e nome) do administrador da empresa: a senha é dele, via convite. */
    public record CreateTenantRequest(String name, String slug, String planCode, String adminEmail, String adminNome,
                                      LocalDateTime currentPeriodEnd) {
    }

    public record TenantCreatedDto(TenantDto tenant, SubscriptionDto subscription, UserDto admin) {
    }

    // ---- planos e features ----
    public record PlanDto(Long id, String code, String name, String description, boolean active) {
    }

    public record PlanRequest(String code, String name, String description, Boolean active) {
    }

    public record FeatureDto(Long id, String code, String name, String description, FeatureKind kind, boolean active) {
    }

    public record PlanFeatureRequest(String featureCode, Boolean enabled, Long limit) {
    }

    public record PlanFeaturesRequest(List<PlanFeatureRequest> features) {
    }

    public record PlanFeatureDto(String featureCode, FeatureKind kind, boolean enabled, Long limit) {
    }

    // ---- assinatura ----
    public record SubscriptionDto(Long id, Long tenantId, String planCode, SubscriptionStatus status, LocalDateTime startedAt,
                                  LocalDateTime currentPeriodStart, LocalDateTime currentPeriodEnd,
                                  LocalDateTime gracePeriodEnd, LocalDateTime canceledAt) {
    }

    public record SubscriptionRequest(String planCode, SubscriptionStatus status, LocalDateTime currentPeriodStart,
                                      LocalDateTime currentPeriodEnd, LocalDateTime gracePeriodEnd) {
    }

    // ---- entitlements ----
    public record OverrideRequest(OverrideEffect effect, Long limit) {
    }

    public record OverrideDto(String featureCode, OverrideEffect effect, Long limit) {
    }

    public record EntitlementDto(String code, FeatureKind kind, Long limit, String source) {
    }

    public record EntitlementsDto(Long tenantId, List<EntitlementDto> entitlements) {
    }

    // ---- branding ----
    /** Somente tokens visuais permitidos. Cor nula = padrão do Design System. Logo: nunca binário nem URL. */
    public record BrandingRequest(String primaryColor, String secondaryColor, String accentColor) {
    }

    public record BrandingDto(Long tenantId, String primaryColor, String secondaryColor, String accentColor,
                              boolean logoConfigured, boolean tenantMayEditColors, boolean tenantMayEditLogo) {
    }

    // ---- financeiro (Fase 4.4.2). Valores monetários SEMPRE BigDecimal (2 casas), nunca double. ----
    public record PageDto<T>(List<T> items, int page, int size, long total) {
    }

    public record InvoiceDto(Long id, Long tenantId, String tenantName, Long subscriptionId, LocalDate referencePeriod,
                             BigDecimal amount, String currency, LocalDate dueDate, LocalDate graceEndsOn,
                             InvoiceStatus status, CollectionPhase phase, LocalDateTime paidAt,
                             LocalDateTime canceledAt, LocalDateTime createdAt) {
    }

    /** {@code referencePeriod} nulo = mês corrente (UTC). O tenant vem SEMPRE do path. */
    public record GenerateInvoiceRequest(LocalDate referencePeriod) {
    }

    public record InvoiceGenerationDto(InvoiceDto invoice, boolean created) {
    }

    /** Pagamento manual integral. Sem tenant/valor livre: o valor deve coincidir com o da cobrança. */
    public record PayInvoiceRequest(BigDecimal amount, LocalDateTime occurredAt, String externalRef) {
    }

    public record PaymentDto(Long id, Long invoiceId, Long tenantId, PaymentKind kind, PaymentMethod method,
                             BigDecimal amount, String currency, LocalDateTime occurredAt,
                             Long registeredByUserId, String externalRef) {
    }

    /** Termos contratados. Campos nulos = manter o valor atual (atualização parcial). */
    public record BillingTermsRequest(BigDecimal contractedAmount, String currency, Integer billingDay, Integer graceDays) {
    }

    public record BillingTermsDto(Long tenantId, String planCode, SubscriptionStatus subscriptionStatus,
                                  BigDecimal contractedAmount, String currency, BillingInterval billingInterval,
                                  int billingDay, int graceDays) {
    }

    public record TenantBillingDto(Long tenantId, String tenantName, String tenantStatus, String suspensionReason,
                                   LocalDateTime suspendedAt, BillingTermsDto terms,
                                   long invoicesOpen, long invoicesOverdue, long invoicesBeyondGrace, long invoicesPaid,
                                   Map<String, BigDecimal> outstandingAmount, Map<String, BigDecimal> overdueAmount,
                                   LocalDateTime lastPaymentAt, List<InvoiceDto> recentInvoices) {
    }

    public record BillingDashboardDto(LocalDate today, LocalDate periodFrom, LocalDate periodTo, int upcomingWindowDays,
                                      long tenantsActive, long tenantsSuspended, long tenantsSuspendedNonPayment,
                                      long subscriptionsActive, long subscriptionsPastDue, long subscriptionsCanceled,
                                      long invoicesOpen, long invoicesOverdue, long invoicesUpcoming,
                                      long invoicesInGrace, long invoicesBeyondGrace, long invoicesPaid,
                                      Map<String, BigDecimal> receivedInPeriod, Map<String, BigDecimal> pendingAmount,
                                      Map<String, BigDecimal> overdueAmount, Map<String, BigDecimal> upcomingAmount) {
    }

    public record RefreshResultDto(int invoicesMarkedOverdue) {
    }
}
