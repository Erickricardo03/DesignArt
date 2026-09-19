package com.designart.admin;

import com.designart.admin.dto.AdminDtos.*;
import com.designart.dto.UserDto;
import com.designart.security.SuperAdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints GLOBAIS do Nexus Control Center (empresas, assinaturas, overrides, branding).
 * Somente role explícita SUPER_ADMIN (nunca "tenant_id nulo"). Isto NÃO dá ao SUPER_ADMIN acesso aos
 * endpoints de negócio dos tenants (continuam exigindo TENANT_ADMIN/USER). O tenant alvo vem do path.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@SuperAdminOnly
@RequiredArgsConstructor
public class AdminTenantController {

    private final AdminTenantService tenants;
    private final AdminSubscriptionService subscriptions;
    private final AdminBrandingService branding;

    @GetMapping
    public List<TenantDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return tenants.list(page, size);
    }

    @GetMapping("/{id}")
    public TenantDto get(@PathVariable Long id) {
        return tenants.get(id);
    }

    @PostMapping
    public ResponseEntity<TenantCreatedDto> create(@RequestBody CreateTenantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenants.create(req));
    }

    @PostMapping("/{id}/suspend")
    public TenantDto suspend(@PathVariable Long id) {
        return tenants.suspend(id);
    }

    @PostMapping("/{id}/reactivate")
    public TenantDto reactivate(@PathVariable Long id) {
        return tenants.reactivate(id);
    }

    @PostMapping("/{id}/users/{userId}/resend-invite")
    public UserDto resendInvite(@PathVariable Long id, @PathVariable Long userId) {
        return tenants.resendInvite(id, userId);
    }

    // ---- assinatura ----
    @GetMapping("/{id}/subscription")
    public SubscriptionDto subscription(@PathVariable Long id) {
        return subscriptions.getSubscription(id);
    }

    @PutMapping("/{id}/subscription")
    public SubscriptionDto putSubscription(@PathVariable Long id, @RequestBody SubscriptionRequest req) {
        return subscriptions.upsertSubscription(id, req);
    }

    // ---- entitlements ----
    @GetMapping("/{id}/entitlements")
    public EntitlementsDto entitlements(@PathVariable Long id) {
        return subscriptions.entitlements(id);
    }

    @GetMapping("/{id}/feature-overrides")
    public List<OverrideDto> overrides(@PathVariable Long id) {
        return subscriptions.listOverrides(id);
    }

    @PutMapping("/{id}/feature-overrides/{featureCode}")
    public OverrideDto putOverride(@PathVariable Long id, @PathVariable String featureCode, @RequestBody OverrideRequest req) {
        return subscriptions.setOverride(id, featureCode, req);
    }

    @DeleteMapping("/{id}/feature-overrides/{featureCode}")
    public ResponseEntity<Void> deleteOverride(@PathVariable Long id, @PathVariable String featureCode) {
        subscriptions.removeOverride(id, featureCode);
        return ResponseEntity.noContent().build();
    }

    // ---- branding (preparação) ----
    @GetMapping("/{id}/branding")
    public BrandingDto branding(@PathVariable Long id) {
        return branding.get(id);
    }

    @PutMapping("/{id}/branding")
    public BrandingDto putBranding(@PathVariable Long id, @RequestBody BrandingRequest req) {
        return branding.update(id, req);
    }

    @DeleteMapping("/{id}/branding")
    public BrandingDto resetBranding(@PathVariable Long id) {
        return branding.reset(id);
    }
}
