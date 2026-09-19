package com.designart.admin;

import com.designart.admin.dto.AdminDtos.*;
import com.designart.audit.*;
import com.designart.billing.*;
import com.designart.entitlement.EffectiveEntitlements;
import com.designart.entitlement.EntitlementService;
import com.designart.exception.InvalidRequestException;
import com.designart.exception.ResourceNotFoundException;
import com.designart.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Assinaturas e overrides de features por tenant (plano de controle, SUPER_ADMIN).
 * <p>
 * IMPORTANTE: assinatura (situação COMERCIAL) e {@code Tenant.status} (situação OPERACIONAL) são
 * independentes: nada aqui altera o status do tenant, e suspender o tenant não altera a assinatura.
 * A suspensão automática por inadimplência (ACTIVE -> PAST_DUE -> fim da carência -> SUSPENSO) é etapa futura.
 * Este serviço nunca recebe tenantId de um corpo: vem do path administrativo.
 */
@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final FeatureRepository featureRepository;
    private final TenantFeatureOverrideRepository overrideRepository;
    private final EntitlementService entitlementService;
    private final AuditService auditService;
    private final AuditActors auditActors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SubscriptionDto getSubscription(Long tenantId) {
        exigirTenant(tenantId);
        return subscriptionRepository.findByTenantId(tenantId).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Este tenant ainda não possui assinatura."));
    }

    /** Cria ou substitui (PUT) a assinatura corrente do tenant. */
    @Transactional
    public SubscriptionDto upsertSubscription(Long tenantId, SubscriptionRequest req) {
        exigirTenant(tenantId);
        if (req == null || req.status() == null) {
            throw new InvalidRequestException("Informe o status da assinatura.");
        }
        Plan plan = planRepository.findByCode(AdminRules.code(req.planCode()))
                .orElseThrow(() -> new InvalidRequestException("Plano inexistente."));
        validarDatas(req.currentPeriodStart(), req.currentPeriodEnd(), req.gracePeriodEnd());
        LocalDateTime now = LocalDateTime.now(clock);

        Subscription atual = subscriptionRepository.findByTenantId(tenantId).orElse(null);
        boolean criando = atual == null;
        SubscriptionStatus antes = criando ? null : atual.getStatus();
        boolean planoMudou = criando || !atual.getPlanId().equals(plan.getId());
        if (planoMudou && !plan.isActive()) {
            throw new InvalidRequestException("O plano informado está inativo e não aceita novas assinaturas.");
        }

        Subscription s = criando ? new Subscription() : atual;
        if (criando) {
            s.setTenantId(tenantId);
            s.setStartedAt(now);
            s.setCreatedAt(now);
        }
        s.setPlanId(plan.getId());
        s.setStatus(req.status());
        s.setCurrentPeriodStart(req.currentPeriodStart() != null ? req.currentPeriodStart() : (criando ? now : s.getCurrentPeriodStart()));
        s.setCurrentPeriodEnd(req.currentPeriodEnd());
        s.setGracePeriodEnd(req.gracePeriodEnd());
        if (req.status() == SubscriptionStatus.CANCELED) {
            if (s.getCanceledAt() == null) {
                s.setCanceledAt(now);
            }
        } else {
            s.setCanceledAt(null);
        }
        validarDatas(s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getGracePeriodEnd());
        s.setUpdatedAt(now);
        s = subscriptionRepository.saveAndFlush(s);

        auditService.success(criando ? AuditAction.SUBSCRIPTION_CREATED : AuditAction.SUBSCRIPTION_CHANGED,
                auditActors.current(), AuditTarget.entity(tenantId, AuditEntityType.SUBSCRIPTION, s.getId()),
                AuditMetadata.builder().subscriptionStatusChange(antes, s.getStatus()).planChanged(planoMudou && !criando).build());
        return toDto(s);
    }

    // ------------------------------------------------------------------ overrides
    @Transactional(readOnly = true)
    public List<OverrideDto> listOverrides(Long tenantId) {
        exigirTenant(tenantId);
        var features = featureRepository.findAll().stream().collect(java.util.stream.Collectors.toMap(Feature::getId, f -> f));
        return overrideRepository.findByTenantId(tenantId).stream()
                .filter(o -> features.containsKey(o.getFeatureId()))
                .sorted(Comparator.comparing(o -> features.get(o.getFeatureId()).getCode()))
                .map(o -> new OverrideDto(features.get(o.getFeatureId()).getCode(), o.getEffect(), o.getLimitValue()))
                .toList();
    }

    @Transactional
    public OverrideDto setOverride(Long tenantId, String featureCode, OverrideRequest req) {
        exigirTenant(tenantId);
        if (req == null || req.effect() == null) {
            throw new InvalidRequestException("Informe o efeito do override (ALLOW ou DENY).");
        }
        Feature feature = featureRepository.findByCode(AdminRules.code(featureCode))
                .filter(Feature::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionalidade não encontrada."));
        Long limit = AdminRules.limit(req.limit());
        if (req.effect() == OverrideEffect.DENY && limit != null) {
            throw new InvalidRequestException("Um override DENY não aceita limite.");
        }
        if (req.effect() == OverrideEffect.ALLOW) {
            if (feature.getKind() == FeatureKind.BOOLEAN && limit != null) {
                throw new InvalidRequestException("Esta funcionalidade não aceita limite numérico.");
            }
            if (feature.getKind() == FeatureKind.LIMIT && limit == null) {
                throw new InvalidRequestException("Informe o limite numérico desta funcionalidade.");
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        TenantFeatureOverride o = overrideRepository.findByTenantIdAndFeatureId(tenantId, feature.getId()).orElseGet(() -> {
            TenantFeatureOverride novo = new TenantFeatureOverride();
            novo.setTenantId(tenantId);
            novo.setFeatureId(feature.getId());
            novo.setCreatedAt(now);
            return novo;
        });
        o.setEffect(req.effect());
        o.setLimitValue(limit);
        o.setUpdatedAt(now);
        o.setUpdatedByUserId(auditActors.current().userId());
        overrideRepository.saveAndFlush(o);
        auditService.success(AuditAction.TENANT_FEATURE_OVERRIDE_CHANGED, auditActors.current(),
                AuditTarget.entity(tenantId, AuditEntityType.FEATURE, feature.getId()),
                AuditMetadata.builder().overrideEffect(req.effect()).build());
        return new OverrideDto(feature.getCode(), o.getEffect(), o.getLimitValue());
    }

    @Transactional
    public void removeOverride(Long tenantId, String featureCode) {
        exigirTenant(tenantId);
        Feature feature = featureRepository.findByCode(AdminRules.code(featureCode))
                .orElseThrow(() -> new ResourceNotFoundException("Funcionalidade não encontrada."));
        TenantFeatureOverride o = overrideRepository.findByTenantIdAndFeatureId(tenantId, feature.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Override não encontrado."));
        overrideRepository.delete(o);
        overrideRepository.flush();
        auditService.success(AuditAction.TENANT_FEATURE_OVERRIDE_CHANGED, auditActors.current(),
                AuditTarget.entity(tenantId, AuditEntityType.FEATURE, feature.getId()),
                AuditMetadata.builder().overrideEffect(null).build());
    }

    @Transactional(readOnly = true)
    public EntitlementsDto entitlements(Long tenantId) {
        exigirTenant(tenantId);
        EffectiveEntitlements e = entitlementService.resolve(tenantId);
        List<EntitlementDto> list = e.byCode().values().stream()
                .sorted(Comparator.comparing(EffectiveEntitlements.Entitlement::code))
                .map(x -> new EntitlementDto(x.code(), x.kind(), x.limit(), x.source().name())).toList();
        return new EntitlementsDto(tenantId, list);
    }

    // ------------------------------------------------------------------ internos
    private void exigirTenant(Long tenantId) {
        if (tenantId == null || !tenantRepository.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant não encontrado.");
        }
    }

    private static void validarDatas(LocalDateTime start, LocalDateTime end, LocalDateTime grace) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new InvalidRequestException("O fim do período deve ser posterior ao início.");
        }
        if (grace != null && end != null && grace.isBefore(end)) {
            throw new InvalidRequestException("O fim da carência não pode ser anterior ao fim do período.");
        }
    }

    SubscriptionDto toDto(Subscription s) {
        String planCode = planRepository.findById(s.getPlanId()).map(Plan::getCode).orElse(null);
        return new SubscriptionDto(s.getId(), s.getTenantId(), planCode, s.getStatus(), s.getStartedAt(),
                s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getGracePeriodEnd(), s.getCanceledAt());
    }
}
