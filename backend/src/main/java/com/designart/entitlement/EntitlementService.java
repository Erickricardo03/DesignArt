package com.designart.entitlement;

import com.designart.billing.*;
import com.designart.tenant.TenantContext;
import com.designart.entitlement.EffectiveEntitlements.Entitlement;
import com.designart.entitlement.EffectiveEntitlements.Source;
import com.designart.exception.ForbiddenOperationException;
import com.designart.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Serviço CENTRAL de entitlements: PLAN FEATURES + TENANT OVERRIDES = EFFECTIVE ENTITLEMENTS.
 * <p>
 * Regras (falham fechado):
 * <ul>
 *   <li>tenant inexistente/nulo => nada;</li>
 *   <li>features do PLANO só valem com assinatura ACTIVE ou PAST_DUE (PAST_DUE = período de carência:
 *       a suspensão automática é etapa futura; CANCELED e ausência de assinatura não concedem o plano);</li>
 *   <li>o plano ser inativo NÃO remove entitlements de assinaturas já existentes (só impede novas);</li>
 *   <li>feature desativada no catálogo nunca é concedida;</li>
 *   <li>override ALLOW concede (com limite, se LIMIT) e DENY remove — valem inclusive sem plano;</li>
 *   <li>entradas LIMIT sempre têm valor; BOOLEAN nunca têm.</li>
 * </ul>
 * O tenant de {@link #hasCurrentTenantFeature} vem de {@link TenantContext} (identidade autenticada);
 * os métodos com {@code tenantId} explícito são para o plano de controle (SUPER_ADMIN) e para services
 * que já obtiveram o tenant de fonte confiável — nunca de um valor enviado pelo frontend.
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    static final String MSG_FEATURE_INDISPONIVEL = "Esta funcionalidade não está disponível no plano da sua empresa.";

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final TenantFeatureOverrideRepository overrideRepository;
    private final FeatureRepository featureRepository;

    @Transactional(readOnly = true)
    public EffectiveEntitlements resolve(Long tenantId) {
        if (tenantId == null || !tenantRepository.existsById(tenantId)) {
            return new EffectiveEntitlements(tenantId, Map.of());
        }
        Map<Long, Feature> features = featureRepository.findAll().stream()
                .filter(Feature::isActive)
                .collect(Collectors.toMap(Feature::getId, Function.identity()));
        Map<String, Entitlement> result = new HashMap<>();

        subscriptionRepository.findByTenantId(tenantId)
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE || s.getStatus() == SubscriptionStatus.PAST_DUE)
                .ifPresent(s -> {
                    for (PlanFeature pf : planFeatureRepository.findByPlanId(s.getPlanId())) {
                        Feature f = features.get(pf.getFeatureId());
                        if (f != null && pf.isEnabled()) {
                            addIfValid(result, f, pf.getLimitValue(), Source.PLAN);
                        }
                    }
                });

        List<TenantFeatureOverride> overrides = overrideRepository.findByTenantId(tenantId);
        for (TenantFeatureOverride o : overrides) {
            Feature f = features.get(o.getFeatureId());
            if (f == null) {
                continue;
            }
            if (o.getEffect() == OverrideEffect.DENY) {
                result.remove(f.getCode());
            } else {
                addIfValid(result, f, o.getLimitValue(), Source.OVERRIDE);
            }
        }
        return new EffectiveEntitlements(tenantId, result);
    }

    private static void addIfValid(Map<String, Entitlement> result, Feature f, Long limit, Source source) {
        if (f.getKind() == FeatureKind.LIMIT) {
            if (limit == null) {
                return; // LIMIT sem valor = configuração inválida: não concede (falha fechada)
            }
            result.put(f.getCode(), new Entitlement(f.getCode(), f.getKind(), limit, source));
        } else {
            result.put(f.getCode(), new Entitlement(f.getCode(), f.getKind(), null, source));
        }
    }

    public boolean hasFeature(Long tenantId, String featureCode) {
        return resolve(tenantId).has(featureCode);
    }

    public OptionalLong getLimit(Long tenantId, String featureCode) {
        return resolve(tenantId).limit(featureCode);
    }

    /** Tenant da identidade autenticada (TenantContext); falha fechada sem contexto. */
    public boolean hasCurrentTenantFeature(String featureCode) {
        return hasFeature(TenantContext.require(), featureCode);
    }

    public OptionalLong getCurrentTenantLimit(String featureCode) {
        return getLimit(TenantContext.require(), featureCode);
    }

    /** Para uso futuro em endpoints de negócio: 403 se o tenant autenticado não tem a feature. */
    public void requireCurrentTenantFeature(String featureCode) {
        if (!hasCurrentTenantFeature(featureCode)) {
            throw new ForbiddenOperationException(MSG_FEATURE_INDISPONIVEL);
        }
    }
}
