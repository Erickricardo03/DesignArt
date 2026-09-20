package com.designart.observability;

import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import com.designart.support.SupportTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Observabilidade (Fase 4.4.3): correlation ID, captura centralizada de 5xx, fingerprint/agrupamento, painel, saúde. */
class ObservabilityIntegrationTest extends SupportTestBase {

    @Autowired ApplicationErrorEventRepository errors;
    @Autowired ObservabilityService observability;

    /** Executa; uma exceção não tratada escapa do MockMvc como ServletException (num servidor real seria 500). */
    private MvcResult chamar(MockHttpServletRequestBuilder req, String jwt) throws Exception {
        try {
            return executar(req, jwt);
        } catch (ServletException e) {
            return null;
        }
    }

    private String jwtAdmin(String email, Tenant t) throws Exception {
        criarUsuario(email, Role.TENANT_ADMIN, t.getId());
        return login(email);
    }

    private List<ApplicationErrorEvent> doTenant(Long tenantId) {
        return errors.findAll().stream().filter(e -> Objects.equals(e.getTenantId(), tenantId)).toList();
    }

    // ------------------------------------------------------------------ correlation ID
    @Test
    void correlationId_ecoaSeValido_geraSeInvalido_eAparecemEmTodasAsRespostas() throws Exception {
        MvcResult ok = executar(get("/api/auth/ping").header("X-Request-Id", "req-abc-12345"), null);
        assertThat(ok.getResponse().getHeader("X-Request-Id")).isEqualTo("req-abc-12345");

        for (String ruim : List.of("curto", "com espaço no meio 12345", "x".repeat(65), "a;b<script>12345", "ok\u0000idnull12")) {
            MvcResult r = executar(get("/api/auth/ping").header("X-Request-Id", ruim), null);
            String usado = r.getResponse().getHeader("X-Request-Id");
            assertThat(usado).as("ID inválido descartado: " + ruim).isNotEqualTo(ruim);
            assertThat(UUID.fromString(usado)).isNotNull();
        }
        String gerado = executar(get("/api/auth/ping"), null).getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(gerado)).isNotNull();
        assertThat(executar(get("/api/clientes"), null).getResponse().getHeader("X-Request-Id")).as("também em 401").isNotNull();
        assertThat(executar(get("/api/auth/ping"), null).getResponse().getHeader("X-Request-Id")).as("IDs não repetem").isNotEqualTo(gerado);
        // o ID nunca autentica
        assertThat(status(get("/api/admin/plans").header("X-Request-Id", "req-abc-12345"), null)).isEqualTo(401);
    }

    // ------------------------------------------------------------------ captura
    @Test
    void erro5xx_eCapturadoComTenantUsuarioECorrelation_semNenhumSegredo() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User admin = criarUsuario("obs.cap@teste.local", Role.TENANT_ADMIN, t.getId());
        String jwt = login("obs.cap@teste.local");
        String corr = "corr-captura-12345";

        MvcResult r = chamar(get("/api/test-observability/boom/42?token=SEGREDO-NA-QUERY&senha=abc").header("X-Request-Id", corr)
                .header("Cookie", "sessao=COOKIE-SECRETO"), jwt);
        assertThat(r).as("exceção não tratada escapa do MockMvc").isNull();

        List<ApplicationErrorEvent> ev = doTenant(t.getId());
        assertThat(ev).hasSize(1);
        ApplicationErrorEvent e = ev.get(0);
        assertThat(e.getTenantId()).isEqualTo(t.getId());
        assertThat(e.getUserId()).isEqualTo(admin.getId());
        assertThat(e.getSupportSessionId()).isNull();
        assertThat(e.getCorrelationId()).isEqualTo(corr);
        assertThat(e.getRequestMethod()).isEqualTo("GET");
        assertThat(e.getRequestPath()).isEqualTo("/api/test-observability/boom/{id}");
        assertThat(e.getHttpStatus()).isEqualTo(500);
        assertThat(e.getErrorCategory()).isEqualTo(ErrorCategory.INTERNAL);
        assertThat(e.getExceptionClass()).isEqualTo("java.lang.IllegalStateException");
        assertThat(e.getOccurrenceCount()).isEqualTo(1);
        assertThat(e.getResolvedAt()).isNull();

        String dump = jdbc.queryForList("select * from application_error_events").toString();
        for (String proibido : List.of("segredo-falso", "hunter2", "Bearer", "SEGREDO-NA-QUERY", "COOKIE-SECRETO", "senha=abc", "token=", jwt,
                "Authorization", "id=42")) {
            assertThat(dump).as("não pode armazenar: " + proibido).doesNotContain(proibido);
        }
        assertThat(e.getSafeMessage()).isEqualTo("Erro interno inesperado.");
        assertThat(e.getRequestPath()).doesNotContain("?");
    }

    @Test
    void erro5xxTratado_devolveOMesmoCorrelationIdDaResposta_eCategoriaIntegracao() throws Exception {
        Tenant t = novoTenant("ATIVO");
        String jwt = jwtAdmin("obs.503@teste.local", t);
        MvcResult r = executar(get("/api/test-observability/unavailable"), jwt);
        assertThat(r.getResponse().getStatus()).isEqualTo(503);
        String corr = r.getResponse().getHeader("X-Request-Id");
        ApplicationErrorEvent e = doTenant(t.getId()).get(0);
        assertThat(e.getCorrelationId()).isEqualTo(corr);
        assertThat(e.getErrorCategory()).isEqualTo(ErrorCategory.INTEGRATION);
        assertThat(e.getErrorCode()).isEqualTo("INTEGRATION_UNAVAILABLE");
        assertThat(e.getHttpStatus()).isEqualTo(503);
        assertThat(corpo(r)).as("a resposta original segue exatamente como antes").contains("Servico de e-mail indisponivel");
    }

    @Test
    void erroDeBanco_temCategoriaDatabase() throws Exception {
        Tenant t = novoTenant("ATIVO");
        String jwt = jwtAdmin("obs.db@teste.local", t);
        assertThat(chamar(get("/api/test-observability/db"), jwt)).isNull();
        ApplicationErrorEvent e = doTenant(t.getId()).get(0);
        assertThat(e.getErrorCategory()).isEqualTo(ErrorCategory.DATABASE);
        assertThat(e.getErrorCode()).isEqualTo("DATABASE_ERROR");
        assertThat(jdbc.queryForList("select * from application_error_events where id = ?", e.getId()).toString()).doesNotContain("insert into").doesNotContain("hunter2");
    }

    @Test
    void erros4xxNaoViramIncidente() throws Exception {
        Tenant t = novoTenant("ATIVO");
        String jwt = jwtAdmin("obs.4xx@teste.local", t);
        assertThat(status(get("/api/test-observability/bad"), jwt)).isEqualTo(400);
        assertThat(status(get("/api/admin/plans"), jwt)).isEqualTo(403);
        assertThat(status(get("/api/test-observability/bad"), null)).isEqualTo(401);
        assertThat(doTenant(t.getId())).isEmpty();
    }

    @Test
    void erroDentroDeSupportSession_associaTenantAlvoESessao() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant alvo = novoTenant("ATIVO");
        User pendente = userRepository.save(User.builder().email("pend.obs@teste.local").role(Role.USER).ativo(false)
                .tenantId(alvo.getId()).nomeCompleto("p").permissoes(new HashSet<>()).build());
        String id = elevarOk(sa, iniciarOk(sa, alvo.getId()));
        emails.setEnabled(false); // resend-invite -> 503 (integração de e-mail indisponível)
        MvcResult r = postJson("/api/admin/support/sessions/" + id + "/tenant/users/" + pendente.getId() + "/resend-invite", Map.of(), sa.jwt());
        assertThat(r.getResponse().getStatus()).isEqualTo(503);

        List<ApplicationErrorEvent> ev = doTenant(alvo.getId());
        assertThat(ev).hasSize(1);
        ApplicationErrorEvent e = ev.get(0);
        assertThat(e.getTenantId()).as("tenant ALVO da sessão").isEqualTo(alvo.getId());
        assertThat(e.getUserId()).as("identidade real: o SUPER_ADMIN").isEqualTo(sa.id());
        assertThat(e.getSupportSessionId()).isNotNull();
        assertThat(e.getSupportSessionId()).isEqualTo(jdbc.queryForObject("select id from support_sessions where public_id = ?", Long.class,
                UUID.fromString(id)));
        assertThat(e.getCorrelationId()).isEqualTo(r.getResponse().getHeader("X-Request-Id"));
        assertThat(e.getErrorCategory()).isEqualTo(ErrorCategory.INTEGRATION);
        // e o erro aparece na visão de suporte do próprio tenant
        emails.setEnabled(true);
        assertThat(json(obter("/api/admin/support/sessions/" + id + "/tenant/errors", sa.jwt())).get("items")).hasSize(1);
    }

    // ------------------------------------------------------------------ fingerprint e agrupamento
    @Test
    void errosRepetidosAgrupam_porTenantEFingerprint_eIdsDiferentesNaRotaSaoOMesmoErro() throws Exception {
        Tenant a = novoTenant("ATIVO");
        Tenant b = novoTenant("ATIVO");
        String jwtA = jwtAdmin("obs.agr.a@teste.local", a);
        String jwtB = jwtAdmin("obs.agr.b@teste.local", b);
        for (int i = 1; i <= 3; i++) {
            chamar(get("/api/test-observability/boom/" + i), jwtA);
        }
        chamar(get("/api/test-observability/boom/999"), jwtB);
        chamar(get("/api/test-observability/db"), jwtA);

        List<ApplicationErrorEvent> deA = doTenant(a.getId());
        assertThat(deA).hasSize(2);
        ApplicationErrorEvent boom = deA.stream().filter(e -> e.getRequestPath().contains("boom")).findFirst().orElseThrow();
        assertThat(boom.getOccurrenceCount()).isEqualTo(3);
        assertThat(boom.getFirstOccurredAt()).isBeforeOrEqualTo(boom.getOccurredAt());
        assertThat(doTenant(b.getId())).hasSize(1);
        assertThat(doTenant(b.getId()).get(0).getOccurrenceCount()).isEqualTo(1);
        assertThat(doTenant(b.getId()).get(0).getFingerprint()).as("mesmo erro, mesmo fingerprint (por tenant há agrupamento próprio)")
                .isEqualTo(boom.getFingerprint());
        assertThat(boom.getFingerprint()).matches("^[0-9a-f]{64}$");
    }

    @Autowired ErrorRecorder recorder;

    @Test
    void gravacaoConcorrenteDoMesmoErro_geraUmUnicoIncidenteComContagemExata() throws Exception {
        Tenant t = novoTenant("ATIVO");
        int n = 12;
        var classification = ErrorClassifier.classify(new IllegalStateException("x"), 500);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        try {
            java.util.concurrent.CyclicBarrier largada = new java.util.concurrent.CyclicBarrier(n);
            List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                fs.add(pool.submit(() -> {
                    largada.await();
                    recorder.record(new ErrorRecorder.Signal(t.getId(), null, null, "corr-concorrente-1", "GET", "/api/conc/{id}", 500, classification));
                    return null;
                }));
            }
            for (var f : fs) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        List<ApplicationErrorEvent> ev = doTenant(t.getId());
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).getOccurrenceCount()).isEqualTo(n);
    }

    @Test
    void resolverEReabrir_reincidenciaAbreNovoIncidente() throws Exception {
        Tenant t = novoTenant("ATIVO");
        String jwt = jwtAdmin("obs.res@teste.local", t);
        SuperAdmin sa = novoSuperAdmin();
        chamar(get("/api/test-observability/boom/1"), jwt);
        long id = doTenant(t.getId()).get(0).getId();

        JsonNode r1 = json(postJson("/api/admin/observability/errors/" + id + "/resolve", Map.of(), sa.jwt()));
        assertThat(r1.get("resolved").asBoolean()).isTrue();
        assertThat(r1.get("resolvedByUserId").asLong()).isEqualTo(sa.id());
        assertThat(postJson("/api/admin/observability/errors/" + id + "/resolve", Map.of(), sa.jwt()).getResponse().getStatus()).as("idempotente").isEqualTo(200);

        chamar(get("/api/test-observability/boom/2"), jwt); // reincidência depois de resolvido = novo incidente
        assertThat(doTenant(t.getId())).hasSize(2);
        ApplicationErrorEvent novo = doTenant(t.getId()).stream().filter(e -> e.getResolvedAt() == null).findFirst().orElseThrow();
        assertThat(novo.getOccurrenceCount()).isEqualTo(1);
        assertThat(postJson("/api/admin/observability/errors/" + id + "/reopen", Map.of(), sa.jwt()).getResponse().getStatus())
                .as("já há um incidente aberto com o mesmo agrupamento").isEqualTo(409);
        assertThat(postJson("/api/admin/observability/errors/" + novo.getId() + "/resolve", Map.of(), sa.jwt()).getResponse().getStatus()).isEqualTo(200);
        JsonNode reaberto = json(postJson("/api/admin/observability/errors/" + id + "/reopen", Map.of(), sa.jwt()));
        assertThat(reaberto.get("resolved").asBoolean()).isFalse();
        assertThat(reaberto.get("resolvedAt").isNull()).isTrue();
        assertThat(postJson("/api/admin/observability/errors/999999999/resolve", Map.of(), sa.jwt()).getResponse().getStatus()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ painel: filtros, paginação, resumo
    @Test
    void listagemFiltraPaginaEResumeSemVazarEntreTenants() throws Exception {
        Tenant a = novoTenant("ATIVO");
        Tenant b = novoTenant("ATIVO");
        String jwtA = jwtAdmin("obs.lst.a@teste.local", a);
        String jwtB = jwtAdmin("obs.lst.b@teste.local", b);
        SuperAdmin sa = novoSuperAdmin();
        chamar(get("/api/test-observability/boom/1"), jwtA);
        chamar(get("/api/test-observability/boom/2"), jwtA);
        executar(get("/api/test-observability/unavailable"), jwtA);
        chamar(get("/api/test-observability/db"), jwtA);
        chamar(get("/api/test-observability/boom/9"), jwtB);

        String base = "/api/admin/observability/errors?tenantId=" + a.getId();
        JsonNode todos = json(obter(base, sa.jwt()));
        assertThat(todos.get("items")).hasSize(3);
        assertThat(codigosDe(todos)).doesNotContain(String.valueOf(b.getId()));
        assertThat(json(obter(base + "&category=DATABASE", sa.jwt())).get("items")).hasSize(1);
        assertThat(json(obter(base + "&category=INTEGRATION&status=503", sa.jwt())).get("items")).hasSize(1);
        assertThat(json(obter(base + "&status=500", sa.jwt())).get("items")).hasSize(2);
        assertThat(json(obter(base + "&resolved=true", sa.jwt())).get("items")).isEmpty();
        assertThat(json(obter(base + "&resolved=false", sa.jwt())).get("items")).hasSize(3);
        LocalDate hoje = LocalDate.now(clock);
        assertThat(json(obter(base + "&from=" + hoje + "&to=" + hoje.plusDays(1), sa.jwt())).get("items")).hasSize(3);
        assertThat(json(obter(base + "&from=" + hoje.plusDays(1) + "&to=" + hoje.plusDays(2), sa.jwt())).get("items")).isEmpty();
        assertThat(status(get(base + "&from=" + hoje + "&to=" + hoje), sa.jwt())).isEqualTo(400);
        assertThat(status(get(base + "&category=NADA"), sa.jwt())).isEqualTo(400);
        assertThat(status(get("/api/admin/observability/errors?tenantId=999999999"), sa.jwt())).isEqualTo(404);
        JsonNode pag = json(obter(base + "&size=2&page=1", sa.jwt()));
        assertThat(pag.get("items")).hasSize(1);
        assertThat(pag.get("total").asLong()).isEqualTo(3);
        assertThat(json(obter("/api/admin/observability/errors?size=100000", sa.jwt())).get("size").asInt()).isEqualTo(100);
        long algum = todos.get("items").get(0).get("id").asLong();
        assertThat(json(obter("/api/admin/observability/errors/" + algum, sa.jwt())).get("id").asLong()).isEqualTo(algum);
        assertThat(status(get("/api/admin/observability/errors/999999999"), sa.jwt())).isEqualTo(404);

        JsonNode resumo = json(obter("/api/admin/tenants/" + a.getId() + "/observability/summary", sa.jwt()));
        assertThat(resumo.get("tenantId").asLong()).isEqualTo(a.getId());
        assertThat(resumo.get("recentIncidents").asLong()).isEqualTo(3);
        assertThat(resumo.get("recentOccurrences").asLong()).isEqualTo(4);
        assertThat(resumo.get("unresolvedIncidents").asLong()).isEqualTo(3);
        assertThat(resumo.get("occurrencesByCategory").get("INTERNAL").asLong()).isEqualTo(2);
        assertThat(resumo.get("occurrencesByCategory").get("DATABASE").asLong()).isEqualTo(1);
        assertThat(resumo.get("occurrencesByCategory").get("INTEGRATION").asLong()).isEqualTo(1);
        assertThat(resumo.get("topEndpoints").get(0).get("path").asText()).isEqualTo("/api/test-observability/boom/{id}");
        assertThat(resumo.get("topEndpoints").get(0).get("occurrences").asLong()).isEqualTo(2);
        assertThat(resumo.get("lastError").get("tenantId").asLong()).isEqualTo(a.getId());
        assertThat(status(get("/api/admin/tenants/" + a.getId() + "/observability/summary?days=0"), sa.jwt())).isEqualTo(400);
        assertThat(status(get("/api/admin/tenants/999999999/observability/summary"), sa.jwt())).isEqualTo(404);
        Tenant vazio = novoTenant("ATIVO");
        JsonNode v = json(obter("/api/admin/tenants/" + vazio.getId() + "/observability/summary", sa.jwt()));
        assertThat(v.get("recentIncidents").asLong()).isZero();
        assertThat(v.get("lastError").isNull()).isTrue();
    }

    private List<String> codigosDe(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("items").forEach(n -> out.add(n.get("tenantId").asText()));
        return out;
    }

    // ------------------------------------------------------------------ acesso e saúde
    @Test
    void painelESaudeSaoSoParaSuperAdmin() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("obs.acc.t@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("obs.acc.u@teste.local", Role.USER, t.getId());
        String admin = login("obs.acc.t@teste.local");
        String user = login("obs.acc.u@teste.local");
        SuperAdmin sa = novoSuperAdmin();
        for (String rota : List.of("/api/admin/observability/errors", "/api/admin/observability/errors/1",
                "/api/admin/tenants/" + t.getId() + "/observability/summary", "/api/admin/system/health",
                "/api/admin/support/sessions", "/api/admin/support/sessions/current",
                "/api/admin/support/sessions/" + UUID.randomUUID() + "/tenant/overview")) {
            assertThat(status(get(rota), null)).as("anônimo " + rota).isEqualTo(401);
            assertThat(status(get(rota), admin)).as("TENANT_ADMIN " + rota).isEqualTo(403);
            assertThat(status(get(rota), user)).as("USER " + rota).isEqualTo(403);
        }
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/observability/errors/1/resolve"), admin)).isEqualTo(403);
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/observability/errors/1/reopen"), user)).isEqualTo(403);
        assertThat(status(get("/api/admin/system/health"), sa.jwt())).isEqualTo(200);
    }

    @Test
    void saudeEUpESanitizada() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        MvcResult r = obter("/api/admin/system/health", sa.jwt());
        JsonNode h = json(r);
        assertThat(h.get("status").asText()).isEqualTo("UP");
        assertThat(h.get("components").get("database").asText()).isEqualTo("UP");
        assertThat(h.get("components").get("application").asText()).isEqualTo("UP");
        assertThat(h.get("timestamp").isNull()).isFalse();
        assertThat(corpo(r).toLowerCase()).doesNotContain("jdbc").doesNotContain("password").doesNotContain("secret").doesNotContain("h2:mem")
                .doesNotContain("username").doesNotContain("url");
        // nada de Actuator exposto
        for (String rota : List.of("/actuator", "/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/threaddump", "/actuator/configprops")) {
            assertThat(status(get(rota), sa.jwt())).as(rota).isIn(401, 403, 404);
            assertThat(status(get(rota), null)).as(rota + " anônimo").isIn(401, 403, 404);
        }
    }
}
