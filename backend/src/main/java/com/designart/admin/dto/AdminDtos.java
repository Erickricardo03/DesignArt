package com.designart.admin.dto;

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
}
