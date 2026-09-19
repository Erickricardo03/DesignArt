package com.designart.admin;

import com.designart.admin.dto.AdminDtos.BrandingDto;
import com.designart.admin.dto.AdminDtos.BrandingRequest;
import com.designart.audit.*;
import com.designart.billing.FeatureCodes;
import com.designart.billing.TenantBranding;
import com.designart.billing.TenantBrandingRepository;
import com.designart.entitlement.EffectiveEntitlements;
import com.designart.entitlement.EntitlementService;
import com.designart.exception.ResourceNotFoundException;
import com.designart.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Preparação de Tenant Branding (administração global). SOMENTE cores {@code #RRGGBB}; a logo não é
 * gravável por API nesta etapa (upload/storage definitivo é etapa futura: existirá só uma CHAVE opaca
 * de objeto, nunca binário no banco). O tenant vem do path administrativo — futuramente, do tenant
 * autenticado — jamais de um campo do corpo.
 * <p>
 * Relação com entitlements: CUSTOM_COLORS (ou o guarda-chuva CUSTOM_BRANDING) libera as cores e
 * CUSTOM_LOGO (ou CUSTOM_BRANDING) libera a logo PARA O TENANT_ADMIN da empresa (rota futura). O
 * SUPER_ADMIN administra independentemente do plano; as flags informam o que o tenant poderá editar.
 */
@Service
@RequiredArgsConstructor
public class AdminBrandingService {

    private final TenantRepository tenantRepository;
    private final TenantBrandingRepository brandingRepository;
    private final EntitlementService entitlementService;
    private final AuditService auditService;
    private final AuditActors auditActors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BrandingDto get(Long tenantId) {
        exigirTenant(tenantId);
        return toDto(tenantId, brandingRepository.findById(tenantId).orElse(null));
    }

    @Transactional
    public BrandingDto update(Long tenantId, BrandingRequest req) {
        exigirTenant(tenantId);
        String primary = AdminRules.color(req == null ? null : req.primaryColor());
        String secondary = AdminRules.color(req == null ? null : req.secondaryColor());
        String accent = AdminRules.color(req == null ? null : req.accentColor());

        TenantBranding b = brandingRepository.findById(tenantId).orElseGet(() -> {
            TenantBranding novo = new TenantBranding();
            novo.setTenantId(tenantId);
            return novo;
        });
        Set<AuditField> changed = EnumSet.noneOf(AuditField.class);
        if (!Objects.equals(primary, b.getPrimaryColor())) {
            b.setPrimaryColor(primary);
            changed.add(AuditField.PRIMARY_COLOR);
        }
        if (!Objects.equals(secondary, b.getSecondaryColor())) {
            b.setSecondaryColor(secondary);
            changed.add(AuditField.SECONDARY_COLOR);
        }
        if (!Objects.equals(accent, b.getAccentColor())) {
            b.setAccentColor(accent);
            changed.add(AuditField.ACCENT_COLOR);
        }
        if (changed.isEmpty()) {
            return toDto(tenantId, brandingRepository.findById(tenantId).orElse(null));
        }
        if (b.getPrimaryColor() == null && b.getSecondaryColor() == null && b.getAccentColor() == null && b.getLogoRef() == null) {
            brandingRepository.findById(tenantId).ifPresent(brandingRepository::delete); // tudo padrão: sem linha
            brandingRepository.flush();
            b = null;
        } else {
            b.setUpdatedAt(LocalDateTime.now(clock));
            b.setUpdatedByUserId(auditActors.current().userId());
            b = brandingRepository.saveAndFlush(b);
        }
        auditService.success(AuditAction.TENANT_BRANDING_CHANGED, auditActors.current(),
                AuditTarget.entity(tenantId, AuditEntityType.TENANT, tenantId),
                AuditMetadata.builder().fieldsChanged(changed).build());
        return toDto(tenantId, b);
    }

    /** Restaura o tema padrão do Design System. */
    @Transactional
    public BrandingDto reset(Long tenantId) {
        exigirTenant(tenantId);
        brandingRepository.findById(tenantId).ifPresent(existing -> {
            Set<AuditField> changed = EnumSet.noneOf(AuditField.class);
            if (existing.getPrimaryColor() != null) changed.add(AuditField.PRIMARY_COLOR);
            if (existing.getSecondaryColor() != null) changed.add(AuditField.SECONDARY_COLOR);
            if (existing.getAccentColor() != null) changed.add(AuditField.ACCENT_COLOR);
            brandingRepository.delete(existing);
            brandingRepository.flush();
            auditService.success(AuditAction.TENANT_BRANDING_CHANGED, auditActors.current(),
                    AuditTarget.entity(tenantId, AuditEntityType.TENANT, tenantId),
                    AuditMetadata.builder().fieldsChanged(changed).build());
        });
        return toDto(tenantId, null);
    }

    /** O tenant poderá (rota futura do TENANT_ADMIN) editar cores? Decisão sempre via entitlements. */
    public boolean tenantMayEditColors(Long tenantId) {
        EffectiveEntitlements e = entitlementService.resolve(tenantId);
        return e.has(FeatureCodes.CUSTOM_COLORS) || e.has(FeatureCodes.CUSTOM_BRANDING);
    }

    public boolean tenantMayEditLogo(Long tenantId) {
        EffectiveEntitlements e = entitlementService.resolve(tenantId);
        return e.has(FeatureCodes.CUSTOM_LOGO) || e.has(FeatureCodes.CUSTOM_BRANDING);
    }

    private BrandingDto toDto(Long tenantId, TenantBranding b) {
        EffectiveEntitlements e = entitlementService.resolve(tenantId);
        return new BrandingDto(tenantId,
                b == null ? null : b.getPrimaryColor(), b == null ? null : b.getSecondaryColor(),
                b == null ? null : b.getAccentColor(), b != null && b.getLogoRef() != null,
                e.has(FeatureCodes.CUSTOM_COLORS) || e.has(FeatureCodes.CUSTOM_BRANDING),
                e.has(FeatureCodes.CUSTOM_LOGO) || e.has(FeatureCodes.CUSTOM_BRANDING));
    }

    private void exigirTenant(Long tenantId) {
        if (tenantId == null || !tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant não encontrado.");
        }
    }
}
