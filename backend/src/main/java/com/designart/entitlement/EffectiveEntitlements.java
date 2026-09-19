package com.designart.entitlement;

import com.designart.billing.FeatureKind;

import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Resultado imutável de PLANO + OVERRIDES para um tenant. É a ÚNICA forma de decidir o que a empresa
 * contratou; nenhum código deve comparar nome/código de plano.
 */
public record EffectiveEntitlements(Long tenantId, Map<String, Entitlement> byCode) {

    public enum Source {PLAN, OVERRIDE}

    /** Uma funcionalidade efetivamente disponível (BOOLEAN sem limite; LIMIT sempre com limite). */
    public record Entitlement(String code, FeatureKind kind, Long limit, Source source) {
    }

    public EffectiveEntitlements {
        byCode = Map.copyOf(byCode);
    }

    public boolean has(String featureCode) {
        return featureCode != null && byCode.containsKey(featureCode);
    }

    /** Vazio se a feature não está disponível ou não é do tipo LIMIT. */
    public OptionalLong limit(String featureCode) {
        Entitlement e = featureCode == null ? null : byCode.get(featureCode);
        return e == null || e.limit() == null ? OptionalLong.empty() : OptionalLong.of(e.limit());
    }

    public Set<String> codes() {
        return byCode.keySet();
    }
}
