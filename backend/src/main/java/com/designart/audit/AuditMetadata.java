package com.designart.audit;

import com.designart.security.Permission;
import com.designart.billing.InvoiceStatus;
import com.designart.billing.OverrideEffect;
import com.designart.billing.PaymentMethod;
import com.designart.model.SuspensionReason;
import com.designart.billing.SubscriptionStatus;
import com.designart.model.TenantStatus;
import com.designart.security.Role;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Metadata de auditoria montada EXCLUSIVAMENTE por código tipado.
 * <p>
 * Segurança por construção: nenhum método público aceita {@code String},
 * {@code Object}, {@code Map} ou objeto genérico. Os únicos valores possíveis são
 * enums do próprio sistema ({@link Role}, {@link Permission}, {@link AuditField},
 * {@link AuditReason}) e booleanos, e as chaves são um conjunto fixo. Portanto NÃO
 * há como um body HTTP, header, senha, hash, token ou JWT chegar aqui, e o
 * frontend não consegue injetar metadata. O JSON é escrito à mão (sem serializar
 * objetos), com tamanho máximo garantido.
 */
public final class AuditMetadata {

    /** Limite rígido (coincide com a coluna VARCHAR(2000) e o CHECK do banco). */
    public static final int MAX_LENGTH = 2000;

    public static final AuditMetadata EMPTY = new AuditMetadata(Map.of());

    private final Map<String, String> jsonFragments;

    private AuditMetadata(Map<String, String> jsonFragments) {
        this.jsonFragments = jsonFragments;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** JSON compacto ({@code {"roleFrom":"USER"}}) ou {@code null} quando vazio. */
    public String toJson() {
        if (jsonFragments.isEmpty()) {
            return null;
        }
        String json = jsonFragments.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        if (json.length() > MAX_LENGTH) {
            throw new IllegalStateException("Metadata de auditoria excede o limite de " + MAX_LENGTH + " caracteres.");
        }
        return json;
    }

    public static final class Builder {
        private final Map<String, String> fragments = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder roleChange(Role from, Role to) {
            fragments.put("roleFrom", quote(from));
            fragments.put("roleTo", quote(to));
            return this;
        }

        /** Role inicial (criação). */
        public Builder role(Role role) {
            fragments.put("roleTo", quote(role));
            return this;
        }

        public Builder activeChange(boolean from, boolean to) {
            fragments.put("activeFrom", String.valueOf(from));
            fragments.put("activeTo", String.valueOf(to));
            return this;
        }

        public Builder permissions(Set<Permission> permissions) {
            fragments.put("permissions", array(permissions));
            return this;
        }

        public Builder permissionsAdded(Set<Permission> added) {
            fragments.put("permissionsAdded", array(added));
            return this;
        }

        public Builder permissionsRemoved(Set<Permission> removed) {
            fragments.put("permissionsRemoved", array(removed));
            return this;
        }

        /** Somente os NOMES dos campos alterados (nunca os valores). */
        public Builder fieldsChanged(Set<AuditField> fields) {
            fragments.put("fields", array(fields));
            return this;
        }

        public Builder reason(AuditReason reason) {
            fragments.put("reasons", array(EnumSet.of(reason)));
            return this;
        }

        public Builder reasons(Set<AuditReason> reasons) {
            fragments.put("reasons", array(reasons));
            return this;
        }

        public Builder tenantStatusChange(TenantStatus from, TenantStatus to) {
            fragments.put("tenantStatusFrom", quote(from));
            fragments.put("tenantStatusTo", quote(to));
            return this;
        }

        /** {@code from} nulo = assinatura criada agora. */
        public Builder subscriptionStatusChange(SubscriptionStatus from, SubscriptionStatus to) {
            fragments.put("subscriptionStatusFrom", quote(from));
            fragments.put("subscriptionStatusTo", quote(to));
            return this;
        }

        public Builder planChanged(boolean changed) {
            fragments.put("planChanged", String.valueOf(changed));
            return this;
        }

        /** {@code null} = override removido. */
        public Builder overrideEffect(OverrideEffect effect) {
            fragments.put("overrideEffect", quote(effect));
            return this;
        }

        public Builder invoiceStatusChange(InvoiceStatus from, InvoiceStatus to) {
            fragments.put("invoiceStatusFrom", quote(from));
            fragments.put("invoiceStatusTo", quote(to));
            return this;
        }

        public Builder paymentMethod(PaymentMethod method) {
            fragments.put("paymentMethod", quote(method));
            return this;
        }

        /** Motivo da suspensão (nulo = não estava suspenso). */
        public Builder suspensionReason(SuspensionReason reason) {
            fragments.put("suspensionReason", quote(reason));
            return this;
        }

        /** Quantidade de itens afetados (ex.: features de um plano). Só um número. */
        public Builder itemCount(int count) {
            fragments.put("count", String.valueOf(count));
            return this;
        }

        public AuditMetadata build() {
            AuditMetadata metadata = new AuditMetadata(new LinkedHashMap<>(fragments));
            metadata.toJson(); // valida o limite de tamanho
            return metadata;
        }

        private static String quote(Enum<?> value) {
            return value == null ? "null" : "\"" + value.name() + "\"";
        }

        private static String array(Collection<? extends Enum<?>> values) {
            if (values == null) {
                return "[]";
            }
            return values.stream().map(Enum::name).sorted()
                    .map(n -> "\"" + n + "\"")
                    .collect(Collectors.joining(",", "[", "]"));
        }
    }
}
