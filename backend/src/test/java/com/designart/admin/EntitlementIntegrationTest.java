package com.designart.admin;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditEvent;
import com.designart.billing.*;
import com.designart.entitlement.EffectiveEntitlements;
import com.designart.entitlement.EntitlementService;
import com.designart.exception.ForbiddenOperationException;
import com.designart.exception.TenantContextException;
import com.designart.model.Tenant;
import com.designart.security.Role;
import com.designart.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Planos, plan_features, overrides, assinaturas e resolução central de entitlements. */
class EntitlementIntegrationTest extends AdminTestBase {

    @Autowired EntitlementService entitlements;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ planos (API)
    @Test
    void criarEEditarPlano_codigoImutavel_conflitos_eAuditoria() throws Exception {
        String sup = superJwt();
        long antes = maxAuditId();
        String code = uniq("PLANO_API");
        MvcResult r = postJson("/api/admin/plans", Map.of("code", code, "name", "Plano API", "description", "desc"), sup);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        long id = corpoJson(r).get("id").asLong();
        assertThat(corpoJson(r).get("active").asBoolean()).isTrue();

        assertThat(postJson("/api/admin/plans", Map.of("code", code, "name", "Outro"), sup).getResponse().getStatus()).isEqualTo(409);
        for (String ruim : List.of("minusculo", "1COMECA", "COM ESPACO", "A", "X".repeat(51), "TEM-HIFEN", "")) {
            assertThat(postJson("/api/admin/plans", Map.of("code", ruim, "name", "Nome"), sup).getResponse().getStatus()).as(ruim).isEqualTo(400);
        }
        assertThat(postJson("/api/admin/plans", Map.of("code", uniq("PLANO_X"), "name", " "), sup).getResponse().getStatus()).isEqualTo(400);

        MvcResult u = putJson("/api/admin/plans/" + id, Map.of("name", "Plano API v2", "description", "nova", "active", false), sup);
        assertThat(u.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpoJson(u).get("name").asText()).isEqualTo("Plano API v2");
        assertThat(corpoJson(u).get("active").asBoolean()).isFalse();
        assertThat(putJson("/api/admin/plans/" + id, Map.of("code", "OUTRO_CODIGO", "name", "x2"), sup).getResponse().getStatus()).isEqualTo(400);
        assertThat(putJson("/api/admin/plans/999999", Map.of("name", "nome"), sup).getResponse().getStatus()).isEqualTo(404);

        List<AuditAction> acoes = auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes).map(AuditEvent::getAction).toList();
        assertThat(acoes).contains(AuditAction.PLAN_CREATED, AuditAction.PLAN_UPDATED);
        AuditEvent upd = auditRepository.findAllByOrderByIdAsc().stream()
                .filter(e -> e.getId() > antes && e.getAction() == AuditAction.PLAN_UPDATED).findFirst().orElseThrow();
        assertThat(upd.getMetadata()).contains("NOME").contains("DESCRIPTION").contains("ACTIVE").doesNotContain("Plano API v2");
    }

    @Test
    void featuresDoPlano_booleanEEnumeracaoDeLimite_validacoes_eAuditoria() throws Exception {
        String sup = superJwt();
        Plan plano = plano(true);
        Feature bool = feature(FeatureKind.BOOLEAN);
        Feature lim = feature(FeatureKind.LIMIT);
        long antes = maxAuditId();
        String path = "/api/admin/plans/" + plano.getId() + "/features";

        // catálogo global
        assertThat(codigos(corpoJson(obter("/api/admin/features", sup)), "code")).contains(bool.getCode(), lim.getCode());

        // validações
        assertThat(putJson(path, Map.of("features", List.of(Map.of("featureCode", bool.getCode(), "limit", 5))), sup).getResponse().getStatus()).as("BOOLEAN com limite").isEqualTo(400);
        assertThat(putJson(path, Map.of("features", List.of(Map.of("featureCode", lim.getCode()))), sup).getResponse().getStatus()).as("LIMIT sem limite").isEqualTo(400);
        assertThat(putJson(path, Map.of("features", List.of(Map.of("featureCode", lim.getCode(), "limit", -1))), sup).getResponse().getStatus()).isEqualTo(400);
        assertThat(putJson(path, Map.of("features", List.of(Map.of("featureCode", "NAO_EXISTE"))), sup).getResponse().getStatus()).isEqualTo(400);
        assertThat(putJson(path, Map.of("features", List.of(Map.of("featureCode", bool.getCode()), Map.of("featureCode", bool.getCode()))), sup).getResponse().getStatus()).as("repetida").isEqualTo(400);
        assertThat(planFeatureRepository.findByPlanId(plano.getId())).as("nada gravado nas tentativas inválidas").isEmpty();

        MvcResult ok = putJson(path, Map.of("features", List.of(
                Map.of("featureCode", bool.getCode()),
                Map.of("featureCode", lim.getCode(), "limit", 10))), sup);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpoJson(obter(path, sup)).size()).isEqualTo(2);
        // PUT substitui: agora só uma
        putJson(path, Map.of("features", List.of(Map.of("featureCode", bool.getCode(), "enabled", false))), sup);
        assertThat(planFeatureRepository.findByPlanId(plano.getId())).hasSize(1);
        assertThat(planFeatureRepository.findByPlanId(plano.getId()).get(0).isEnabled()).isFalse();

        assertThat(auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes)
                .filter(e -> e.getAction() == AuditAction.PLAN_FEATURES_CHANGED).count()).isEqualTo(2);
        assertThat(obter("/api/admin/plans/999999/features", sup).getResponse().getStatus()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ resolução de entitlements
    @Test
    void entitlementEfetivo_planoMaisOverrides_allowDenyELimites() throws Exception {
        Tenant t = novoTenant("ATIVO");
        Plan plano = plano(true);
        Feature a = feature(FeatureKind.BOOLEAN);   // no plano
        Feature b = feature(FeatureKind.BOOLEAN);   // fora do plano (ALLOW por override)
        Feature c = feature(FeatureKind.BOOLEAN);   // no plano (DENY por override)
        Feature l = feature(FeatureKind.LIMIT);     // limite 10 no plano, 50 por override
        Feature desligada = feature(FeatureKind.BOOLEAN); // linha do plano com enabled=false
        noPlano(plano, a, true, null);
        noPlano(plano, c, true, null);
        noPlano(plano, l, true, 10L);
        noPlano(plano, desligada, false, null);
        assinatura(t.getId(), plano, SubscriptionStatus.ACTIVE);

        EffectiveEntitlements base = entitlements.resolve(t.getId());
        assertThat(base.has(a.getCode())).isTrue();
        assertThat(base.has(c.getCode())).isTrue();
        assertThat(base.has(b.getCode())).isFalse();
        assertThat(base.has(desligada.getCode())).isFalse();
        assertThat(entitlements.getLimit(t.getId(), l.getCode()).getAsLong()).isEqualTo(10);
        assertThat(entitlements.getLimit(t.getId(), a.getCode())).as("BOOLEAN não tem limite").isEmpty();

        String sup = superJwt();
        String base2 = "/api/admin/tenants/" + t.getId() + "/feature-overrides/";
        assertThat(putJson(base2 + b.getCode(), Map.of("effect", "ALLOW"), sup).getResponse().getStatus()).isEqualTo(200);
        assertThat(putJson(base2 + c.getCode(), Map.of("effect", "DENY"), sup).getResponse().getStatus()).isEqualTo(200);
        assertThat(putJson(base2 + l.getCode(), Map.of("effect", "ALLOW", "limit", 50), sup).getResponse().getStatus()).isEqualTo(200);
        assertThat(putJson(base2 + desligada.getCode(), Map.of("effect", "ALLOW"), sup).getResponse().getStatus()).isEqualTo(200);

        assertThat(entitlements.hasFeature(t.getId(), b.getCode())).as("ALLOW concede fora do plano").isTrue();
        assertThat(entitlements.hasFeature(t.getId(), c.getCode())).as("DENY remove do plano").isFalse();
        assertThat(entitlements.hasFeature(t.getId(), a.getCode())).isTrue();
        assertThat(entitlements.getLimit(t.getId(), l.getCode()).getAsLong()).as("override de limite").isEqualTo(50);
        assertThat(entitlements.hasFeature(t.getId(), desligada.getCode())).isTrue();

        // via API administrativa
        var body = corpoJson(obter("/api/admin/tenants/" + t.getId() + "/entitlements", sup));
        List<String> codes = codigos(body.get("entitlements"), "code");
        assertThat(codes).contains(a.getCode(), b.getCode(), l.getCode()).doesNotContain(c.getCode());
        assertThat(codigos(corpoJson(obter("/api/admin/tenants/" + t.getId() + "/feature-overrides", sup)), "featureCode"))
                .contains(b.getCode(), c.getCode(), l.getCode());

        // remover override devolve o comportamento do plano
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(base2 + c.getCode()), sup)).isEqualTo(204);
        assertThat(entitlements.hasFeature(t.getId(), c.getCode())).isTrue();
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(base2 + c.getCode()), sup)).as("já removido").isEqualTo(404);
    }

    @Test
    void overridesSaoPorTenant_semVazarParaOutraEmpresa() throws Exception {
        Tenant t1 = novoTenant("ATIVO");
        Tenant t2 = novoTenant("ATIVO");
        Plan plano = plano(true);
        Feature f = feature(FeatureKind.BOOLEAN);
        noPlano(plano, f, true, null);
        assinatura(t1.getId(), plano, SubscriptionStatus.ACTIVE);
        assinatura(t2.getId(), plano, SubscriptionStatus.ACTIVE);
        putJson("/api/admin/tenants/" + t1.getId() + "/feature-overrides/" + f.getCode(), Map.of("effect", "DENY"), superJwt());
        assertThat(entitlements.hasFeature(t1.getId(), f.getCode())).isFalse();
        assertThat(entitlements.hasFeature(t2.getId(), f.getCode())).isTrue();
    }

    @Test
    void statusDaAssinaturaDefineSePlanoConcede_activePastDueCanceledESemAssinatura() throws Exception {
        Plan plano = plano(true);
        Feature f = feature(FeatureKind.BOOLEAN);
        Feature extra = feature(FeatureKind.BOOLEAN);
        noPlano(plano, f, true, null);
        Tenant t = novoTenant("ATIVO");
        assertThat(entitlements.hasFeature(t.getId(), f.getCode())).as("sem assinatura").isFalse();

        String sup = superJwt();
        String sub = "/api/admin/tenants/" + t.getId() + "/subscription";
        assertThat(putJson(sub, Map.of("planCode", plano.getCode(), "status", "ACTIVE"), sup).getResponse().getStatus()).isEqualTo(200);
        assertThat(entitlements.hasFeature(t.getId(), f.getCode())).as("ACTIVE").isTrue();
        putJson(sub, Map.of("planCode", plano.getCode(), "status", "PAST_DUE"), sup);
        assertThat(entitlements.hasFeature(t.getId(), f.getCode())).as("PAST_DUE (carência)").isTrue();
        putJson(sub, Map.of("planCode", plano.getCode(), "status", "CANCELED"), sup);
        assertThat(entitlements.hasFeature(t.getId(), f.getCode())).as("CANCELED").isFalse();
        // override ALLOW continua valendo explicitamente, mesmo cancelada (contrato personalizado do SUPER_ADMIN)
        putJson("/api/admin/tenants/" + t.getId() + "/feature-overrides/" + extra.getCode(), Map.of("effect", "ALLOW"), sup);
        assertThat(entitlements.hasFeature(t.getId(), extra.getCode())).isTrue();
        assertThat(entitlements.hasFeature(t.getId(), f.getCode())).isFalse();
        // reativar limpa canceled_at
        MvcResult re = putJson(sub, Map.of("planCode", plano.getCode(), "status", "ACTIVE"), sup);
        assertThat(corpoJson(re).get("canceledAt").isNull()).isTrue();
        assertThat(entitlements.hasFeature(t.getId(), f.getCode())).isTrue();
    }

    @Test
    void validacoesDeOverride_eDeAssinatura() throws Exception {
        String sup = superJwt();
        Tenant t = novoTenant("ATIVO");
        Feature bool = feature(FeatureKind.BOOLEAN);
        Feature lim = feature(FeatureKind.LIMIT);
        String base = "/api/admin/tenants/" + t.getId() + "/feature-overrides/";
        assertThat(putJson(base + lim.getCode(), Map.of("effect", "ALLOW"), sup).getResponse().getStatus()).as("LIMIT sem limite").isEqualTo(400);
        assertThat(putJson(base + bool.getCode(), Map.of("effect", "ALLOW", "limit", 3), sup).getResponse().getStatus()).as("BOOLEAN com limite").isEqualTo(400);
        assertThat(putJson(base + lim.getCode(), Map.of("effect", "DENY", "limit", 3), sup).getResponse().getStatus()).as("DENY com limite").isEqualTo(400);
        assertThat(putJson(base + lim.getCode(), Map.of("effect", "ALLOW", "limit", -5), sup).getResponse().getStatus()).isEqualTo(400);
        assertThat(putJson(base + bool.getCode(), Map.of(), sup).getResponse().getStatus()).as("sem effect").isEqualTo(400);
        assertThat(putJson(base + bool.getCode(), Map.of("effect", "TALVEZ"), sup).getResponse().getStatus()).as("enum inválido").isEqualTo(400);
        assertThat(putJson(base + "NAO_EXISTE", Map.of("effect", "ALLOW"), sup).getResponse().getStatus()).isEqualTo(404);
        assertThat(overrideRepository.findByTenantId(t.getId())).isEmpty();

        Plan inativo = plano(false);
        String sub = "/api/admin/tenants/" + t.getId() + "/subscription";
        assertThat(putJson(sub, Map.of("planCode", inativo.getCode(), "status", "ACTIVE"), sup).getResponse().getStatus()).as("plano inativo").isEqualTo(400);
        assertThat(putJson(sub, Map.of("planCode", "NAO_EXISTE", "status", "ACTIVE"), sup).getResponse().getStatus()).isEqualTo(400);
        Plan ativo = plano(true);
        assertThat(putJson(sub, Map.of("planCode", ativo.getCode()), sup).getResponse().getStatus()).as("sem status").isEqualTo(400);
        assertThat(putJson(sub, Map.of("planCode", ativo.getCode(), "status", "SUSPENDED"), sup).getResponse().getStatus()).as("status inválido").isEqualTo(400);
        assertThat(putJson(sub, Map.of("planCode", ativo.getCode(), "status", "ACTIVE",
                "currentPeriodStart", "2030-01-10T00:00:00", "currentPeriodEnd", "2030-01-05T00:00:00"), sup).getResponse().getStatus()).as("fim antes do início").isEqualTo(400);
        assertThat(putJson(sub, Map.of("planCode", ativo.getCode(), "status", "PAST_DUE",
                "currentPeriodStart", "2030-01-01T00:00:00", "currentPeriodEnd", "2030-01-31T00:00:00",
                "gracePeriodEnd", "2030-01-20T00:00:00"), sup).getResponse().getStatus()).as("carência antes do fim").isEqualTo(400);
        assertThat(subscriptionRepository.findByTenantId(t.getId())).isEmpty();
        // carência válida
        MvcResult ok = putJson(sub, Map.of("planCode", ativo.getCode(), "status", "PAST_DUE",
                "currentPeriodStart", "2030-01-01T00:00:00", "currentPeriodEnd", "2030-01-31T00:00:00",
                "gracePeriodEnd", "2030-02-07T00:00:00"), sup);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpoJson(ok).get("gracePeriodEnd").asText()).startsWith("2030-02-07");
    }

    @Test
    void auditoriaDeAssinaturaEOverride_semSegredosENumeros() throws Exception {
        String sup = superJwt();
        Tenant t = novoTenant("ATIVO");
        Plan p1 = plano(true);
        Plan p2 = plano(true);
        Feature f = feature(FeatureKind.BOOLEAN);
        long antes = maxAuditId();
        String sub = "/api/admin/tenants/" + t.getId() + "/subscription";
        putJson(sub, Map.of("planCode", p1.getCode(), "status", "ACTIVE"), sup);
        putJson(sub, Map.of("planCode", p2.getCode(), "status", "PAST_DUE"), sup);
        putJson("/api/admin/tenants/" + t.getId() + "/feature-overrides/" + f.getCode(), Map.of("effect", "ALLOW"), sup);

        List<AuditEvent> novos = auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes).toList();
        AuditEvent criada = novos.stream().filter(e -> e.getAction() == AuditAction.SUBSCRIPTION_CREATED).findFirst().orElseThrow();
        AuditEvent mudou = novos.stream().filter(e -> e.getAction() == AuditAction.SUBSCRIPTION_CHANGED).findFirst().orElseThrow();
        AuditEvent ov = novos.stream().filter(e -> e.getAction() == AuditAction.TENANT_FEATURE_OVERRIDE_CHANGED).findFirst().orElseThrow();
        assertThat(criada.getTargetTenantId()).isEqualTo(t.getId());
        assertThat(mudou.getMetadata()).contains("ACTIVE").contains("PAST_DUE").contains("\"planChanged\":true");
        assertThat(ov.getMetadata()).contains("ALLOW");
        assertThat(novos).allSatisfy(e -> assertThat(e.getActorRole()).isEqualTo(Role.SUPER_ADMIN));
        assertThat(dumpAuditoria()).doesNotContain("eyJ").doesNotContain("$2a$").doesNotContain("Bearer");
    }

    // ------------------------------------------------------------------ contexto autenticado
    @Test
    void tenantDoEntitlementAtualVemDoContexto_eFalhaFechadoSemEle() throws Exception {
        Tenant t = novoTenant("ATIVO");
        Plan plano = plano(true);
        Feature f = feature(FeatureKind.BOOLEAN);
        Feature l = feature(FeatureKind.LIMIT);
        noPlano(plano, f, true, null);
        noPlano(plano, l, true, 7L);
        assinatura(t.getId(), plano, SubscriptionStatus.ACTIVE);

        assertThatThrownBy(() -> entitlements.hasCurrentTenantFeature(f.getCode())).isInstanceOf(TenantContextException.class);
        TenantContext.set(t.getId());
        assertThat(entitlements.hasCurrentTenantFeature(f.getCode())).isTrue();
        assertThat(entitlements.getCurrentTenantLimit(l.getCode()).getAsLong()).isEqualTo(7);
        entitlements.requireCurrentTenantFeature(f.getCode());
        assertThatThrownBy(() -> entitlements.requireCurrentTenantFeature("OUTRA_FEATURE")).isInstanceOf(ForbiddenOperationException.class);
        TenantContext.clear();
        // tenant inexistente/nulo: nada
        assertThat(entitlements.hasFeature(null, f.getCode())).isFalse();
        assertThat(entitlements.hasFeature(99999999L, f.getCode())).isFalse();
        assertThat(entitlements.hasFeature(t.getId(), null)).isFalse();
    }

    @Test
    void featureDesativadaNoCatalogoNuncaEConcedida_eLimiteInvalidoFalhaFechado() throws Exception {
        Tenant t = novoTenant("ATIVO");
        Plan plano = plano(true);
        Feature f = feature(FeatureKind.BOOLEAN);
        Feature l = feature(FeatureKind.LIMIT);
        noPlano(plano, f, true, null);
        noPlano(plano, l, true, null);       // LIMIT sem valor (dado inválido, ex.: edição manual do banco)
        assinatura(t.getId(), plano, SubscriptionStatus.ACTIVE);
        assertThat(entitlements.hasFeature(t.getId(), f.getCode())).isTrue();
        assertThat(entitlements.hasFeature(t.getId(), l.getCode())).as("LIMIT sem valor não concede").isFalse();
        f.setActive(false);
        featureRepository.save(f);
        assertThat(entitlements.hasFeature(t.getId(), f.getCode())).isFalse();
    }
}
