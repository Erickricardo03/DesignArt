package com.designart.admin;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditEvent;
import com.designart.billing.*;
import com.designart.model.Tenant;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Preparação de Tenant Branding: só cores #RRGGBB, sem CSS/HTML, isolado por tenant e ligado a entitlements. */
class TenantBrandingIntegrationTest extends AdminTestBase {

    private Map<String, Object> cores(String p, String s, String a) {
        Map<String, Object> m = new HashMap<>();
        m.put("primaryColor", p);
        m.put("secondaryColor", s);
        m.put("accentColor", a);
        return m;
    }

    @Test
    void coresValidasSaoNormalizadas_persistidasPorTenant_eResetRestauraOPadrao() throws Exception {
        String sup = superJwt();
        Tenant t1 = novoTenant("ATIVO");
        Tenant t2 = novoTenant("ATIVO");
        String p1 = "/api/admin/tenants/" + t1.getId() + "/branding";

        assertThat(corpoJson(obter(p1, sup)).get("primaryColor").isNull()).as("padrão").isTrue();
        MvcResult r = putJson(p1, cores("#1a2b3c", "#FFAA00", null), sup);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpoJson(r).get("primaryColor").asText()).isEqualTo("#1A2B3C");   // normalizada
        assertThat(corpoJson(r).get("accentColor").isNull()).isTrue();
        assertThat(brandingRepository.findById(t1.getId()).orElseThrow().getPrimaryColor()).isEqualTo("#1A2B3C");
        // outro tenant não é afetado
        assertThat(brandingRepository.findById(t2.getId())).isEmpty();
        assertThat(corpoJson(obter("/api/admin/tenants/" + t2.getId() + "/branding", sup)).get("primaryColor").isNull()).isTrue();

        // restaurar tema padrão (DELETE) e (PUT com tudo nulo)
        MvcResult d = executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(p1), sup);
        assertThat(d.getResponse().getStatus()).isEqualTo(200);
        assertThat(brandingRepository.findById(t1.getId())).isEmpty();
        putJson(p1, cores("#000000", null, null), sup);
        assertThat(brandingRepository.findById(t1.getId())).isPresent();
        putJson(p1, cores(null, "  ", ""), sup);
        assertThat(brandingRepository.findById(t1.getId())).as("tudo padrão: sem linha").isEmpty();
    }

    @Test
    void coresMaliciosasOuInvalidasSaoRejeitadas_semCssHtmlOuJavascript() throws Exception {
        String sup = superJwt();
        Tenant t = novoTenant("ATIVO");
        String path = "/api/admin/tenants/" + t.getId() + "/branding";
        for (String ruim : List.of("red", "#FFF", "#GGGGGG", "#12345", "#1234567", "123456", "rgb(0,0,0)", "hsl(10,10%,10%)",
                "var(--x)", "url(http://evil)", "#FFFFFF;background:url(x)", "#FFFFFF\n", "<script>alert(1)</script>",
                "#FFFFFF</style><script>", "javascript:alert(1)", "expression(alert(1))", "#ＦＦＦＦＦＦ", "transparent", "inherit")) {
            for (String campo : List.of("primaryColor", "secondaryColor", "accentColor")) {
                Map<String, Object> m = new HashMap<>();
                m.put(campo, ruim);
                assertThat(putJson(path, m, sup).getResponse().getStatus()).as(campo + "=" + ruim).isEqualTo(400);
            }
        }
        assertThat(brandingRepository.findById(t.getId())).isEmpty();
    }

    @Test
    void logoNaoEGravavelPorApiNestaEtapa_eChaveDeObjetoEValidada() throws Exception {
        String sup = superJwt();
        Tenant t = novoTenant("ATIVO");
        Map<String, Object> m = cores("#112233", null, null);
        m.put("logoRef", "../../etc/passwd");
        m.put("logo", "data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+");
        m.put("logoUrl", "https://evil.example.com/x.png");
        m.put("css", "body{display:none}");
        MvcResult r = putJson("/api/admin/tenants/" + t.getId() + "/branding", m, sup);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        var b = brandingRepository.findById(t.getId()).orElseThrow();
        assertThat(b.getLogoRef()).isNull();
        assertThat(corpoJson(r).get("logoConfigured").asBoolean()).isFalse();
        assertThat(corpo(r)).doesNotContain("passwd").doesNotContain("evil").doesNotContain("display:none");

        // validador de chave opaca (uso futuro do storage): sem caminho relativo, esquema, host ou caracteres especiais
        assertThat(AdminRules.isSafeLogoKey("tenants/12/logo-2026.png")).isTrue();
        for (String ruim : List.of("../x.png", "a/../b.png", "/abs.png", "https://evil/x.png", "a b.png", "x.png?y=1", "a//b.png",
                "<svg>", "", "x".repeat(300), "a\\b.png", "data:image/png;base64,AAAA")) {
            assertThat(AdminRules.isSafeLogoKey(ruim)).as(ruim).isFalse();
        }
        assertThat(AdminRules.isSafeLogoKey(null)).isFalse();
    }

    @Test
    void somenteSuperAdminAdministraBranding_tenantAdminEUserNao() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("br.adm@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("br.user@teste.local", Role.USER, t.getId());
        String path = "/api/admin/tenants/" + t.getId() + "/branding";
        for (String email : List.of("br.adm@teste.local", "br.user@teste.local")) {
            String jwt = login(email);
            assertThat(putJson(path, cores("#010203", null, null), jwt).getResponse().getStatus()).isEqualTo(403);
            assertThat(obter(path, jwt).getResponse().getStatus()).isEqualTo(403);
            assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path), jwt)).isEqualTo(403);
        }
        assertThat(brandingRepository.findById(t.getId())).isEmpty();
        assertThat(obter("/api/admin/tenants/999999/branding", superJwt()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void brandingEEntitlements_flagsSeguemPlanoEOverrides() throws Exception {
        String sup = superJwt();
        Tenant t = novoTenant("ATIVO");
        String path = "/api/admin/tenants/" + t.getId() + "/branding";
        // features comerciais previstas (semeadas na V6; no H2 de teste criamos com os mesmos códigos se faltarem)
        Feature colors = garantir(FeatureCodes.CUSTOM_COLORS);
        Feature logo = garantir(FeatureCodes.CUSTOM_LOGO);
        Feature brand = garantir(FeatureCodes.CUSTOM_BRANDING);
        Plan plano = plano(true);
        assinatura(t.getId(), plano, SubscriptionStatus.ACTIVE);

        var vazio = corpoJson(obter(path, sup));
        assertThat(vazio.get("tenantMayEditColors").asBoolean()).isFalse();
        assertThat(vazio.get("tenantMayEditLogo").asBoolean()).isFalse();

        noPlano(plano, colors, true, null);                                  // por PLANO
        var c = corpoJson(obter(path, sup));
        assertThat(c.get("tenantMayEditColors").asBoolean()).isTrue();
        assertThat(c.get("tenantMayEditLogo").asBoolean()).isFalse();

        putJson("/api/admin/tenants/" + t.getId() + "/feature-overrides/" + logo.getCode(), Map.of("effect", "ALLOW"), sup); // por OVERRIDE
        assertThat(corpoJson(obter(path, sup)).get("tenantMayEditLogo").asBoolean()).isTrue();
        putJson("/api/admin/tenants/" + t.getId() + "/feature-overrides/" + colors.getCode(), Map.of("effect", "DENY"), sup);
        assertThat(corpoJson(obter(path, sup)).get("tenantMayEditColors").asBoolean()).as("DENY vence o plano").isFalse();
        putJson("/api/admin/tenants/" + t.getId() + "/feature-overrides/" + brand.getCode(), Map.of("effect", "ALLOW"), sup); // guarda-chuva
        var u = corpoJson(obter(path, sup));
        assertThat(u.get("tenantMayEditColors").asBoolean()).isTrue();
        assertThat(u.get("tenantMayEditLogo").asBoolean()).isTrue();
    }

    @Test
    void auditoriaDeBranding_registraSomenteNomesDeCampos() throws Exception {
        String sup = superJwt();
        Tenant t = novoTenant("ATIVO");
        long antes = maxAuditId();
        putJson("/api/admin/tenants/" + t.getId() + "/branding", cores("#ABCDEF", "#123456", null), sup);
        putJson("/api/admin/tenants/" + t.getId() + "/branding", cores("#ABCDEF", "#123456", null), sup); // sem mudança: sem evento
        executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/tenants/" + t.getId() + "/branding"), sup);
        List<AuditEvent> ev = auditRepository.findAllByOrderByIdAsc().stream()
                .filter(e -> e.getId() > antes && e.getAction() == AuditAction.TENANT_BRANDING_CHANGED).toList();
        assertThat(ev).hasSize(2);
        assertThat(ev.get(0).getMetadata()).contains("PRIMARY_COLOR").contains("SECONDARY_COLOR").doesNotContain("#");
        assertThat(ev.get(0).getTargetTenantId()).isEqualTo(t.getId());
        assertThat(dumpAuditoria()).doesNotContain("#ABCDEF").doesNotContain("#123456");
    }

    private Feature garantir(String code) {
        return featureRepository.findByCode(code).orElseGet(() -> {
            Feature f = new Feature();
            f.setCode(code);
            f.setName(code);
            f.setKind(FeatureKind.BOOLEAN);
            f.setActive(true);
            return featureRepository.save(f);
        });
    }
}
