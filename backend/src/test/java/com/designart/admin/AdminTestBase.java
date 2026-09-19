package com.designart.admin;

import com.designart.billing.*;
import com.designart.identity.IntegrationTestBase;
import com.designart.security.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Helpers dos testes do Control Center. Tudo em H2/PostgreSQL de teste; e-mails em teste.local. */
public abstract class AdminTestBase extends IntegrationTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected PlanRepository planRepository;
    @Autowired protected FeatureRepository featureRepository;
    @Autowired protected PlanFeatureRepository planFeatureRepository;
    @Autowired protected SubscriptionRepository subscriptionRepository;
    @Autowired protected TenantFeatureOverrideRepository overrideRepository;
    @Autowired protected TenantBrandingRepository brandingRepository;

    /** Cria um SUPER_ADMIN novo (sem tenant) e devolve o JWT dele (login real). */
    protected String superJwt() throws Exception {
        String email = "super" + SEQ.incrementAndGet() + "@teste.local";
        criarUsuario(email, Role.SUPER_ADMIN, null);
        return login(email);
    }

    protected String uniq(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }

    protected Plan plano(boolean active) {
        Plan p = new Plan();
        p.setCode(uniq("PLANO_T"));
        p.setName("Plano de teste");
        p.setActive(active);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return planRepository.save(p);
    }

    protected Feature feature(FeatureKind kind) {
        Feature f = new Feature();
        f.setCode(uniq("TF_"));
        f.setName("Feature de teste");
        f.setKind(kind);
        f.setActive(true);
        return featureRepository.save(f);
    }

    protected PlanFeature noPlano(Plan plan, Feature feature, boolean enabled, Long limit) {
        PlanFeature pf = new PlanFeature();
        pf.setPlanId(plan.getId());
        pf.setFeatureId(feature.getId());
        pf.setEnabled(enabled);
        pf.setLimitValue(limit);
        return planFeatureRepository.save(pf);
    }

    protected Subscription assinatura(Long tenantId, Plan plan, SubscriptionStatus status) {
        Subscription s = new Subscription();
        s.setTenantId(tenantId);
        s.setPlanId(plan.getId());
        s.setStatus(status);
        s.setStartedAt(LocalDateTime.now());
        s.setCurrentPeriodStart(LocalDateTime.now());
        s.setCanceledAt(status == SubscriptionStatus.CANCELED ? LocalDateTime.now() : null);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return subscriptionRepository.save(s);
    }

    protected MvcResult putJson(String path, Object corpo, String token) throws Exception {
        return executar(put(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)), token);
    }

    protected MvcResult obter(String path, String token) throws Exception {
        return executar(get(path), token);
    }

    protected List<String> codigos(com.fasterxml.jackson.databind.JsonNode array, String campo) {
        List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.get(campo).asText()));
        return out;
    }
}
