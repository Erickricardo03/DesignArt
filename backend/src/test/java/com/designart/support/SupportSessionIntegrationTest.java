package com.designart.support;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditEntityType;
import com.designart.audit.AuditEvent;
import com.designart.audit.AuditOutcome;
import com.designart.billing.SubscriptionStatus;
import com.designart.billing.TenantBranding;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Modo Suporte (Fase 4.4.3): identidade real do SUPER_ADMIN, READ_ONLY por padrão, elevação explícita e temporária,
 * allowlist fail-closed, sessão única, expiração/encerramento, tenant suspenso, auditoria sem segredo.
 */
class SupportSessionIntegrationTest extends SupportTestBase {

    @Autowired SupportSessionService sessionService;
    @Autowired SupportSessionRepository sessionRepository;

    private String base(String id) {
        return "/api/admin/support/sessions/" + id;
    }

    // ------------------------------------------------------------------ quem pode iniciar
    @Test
    void somenteSuperAdminIniciaSuporte() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("sp.tadmin.uniq@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("sp.user.uniq@teste.local", Role.USER, t.getId());
        Map<String, Object> corpo = Map.of("tenantId", t.getId(), "reason", motivo("A1"));
        assertThat(postJson("/api/admin/support/sessions", corpo, null).getResponse().getStatus()).isEqualTo(401);
        assertThat(postJson("/api/admin/support/sessions", corpo, login("sp.tadmin.uniq@teste.local")).getResponse().getStatus()).isEqualTo(403);
        assertThat(postJson("/api/admin/support/sessions", corpo, login("sp.user.uniq@teste.local")).getResponse().getStatus()).isEqualTo(403);
        assertThat(sessionRepository.findAll().stream().noneMatch(s -> s.getTenantId().equals(t.getId()))).isTrue();
    }

    @Test
    void validaTenantEMotivo() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        assertThat(iniciar(sa, 999999999L, motivo("B1")).getResponse().getStatus()).as("tenant inexistente").isEqualTo(404);
        Tenant t = novoTenant("ATIVO");
        for (String ruim : Arrays.asList(null, "", "curto", "x".repeat(301), "motivo com controle \u0007 no meio do texto",
                "usar token: abc123 para entrar no sistema", "Authorization Bearer abcdefghijkl para testar",
                "eyJhbGciOiJIUzI1NiJ9.abcdefghijk.abcdefghijk colado aqui")) {
            assertThat(iniciar(sa, t.getId(), ruim).getResponse().getStatus()).as("motivo: " + ruim).isEqualTo(400);
        }
        assertThat(postJson("/api/admin/support/sessions", Map.of("reason", motivo("B2")), sa.jwt()).getResponse().getStatus()).as("sem tenant").isEqualTo(400);
        assertThat(sessionRepository.findFirstBySuperAdminUserIdAndEndedAtIsNull(sa.id())).isEmpty();
    }

    // ------------------------------------------------------------------ sessão, identidade e auditoria
    @Test
    void sessaoNasceReadOnly_expiraEmTrintaMinutos_eIdentidadeRealPermanece() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        long antes = maxAuditId();
        String marca = "MARCA-UNICA-" + UUID.randomUUID();
        MvcResult r = iniciar(sa, t.getId(), motivo(marca));
        assertThat(r.getResponse().getStatus()).as(corpo(r)).isEqualTo(201);
        JsonNode s = json(r);
        assertThat(s.get("mode").asText()).isEqualTo("READ_ONLY");
        assertThat(s.get("active").asBoolean()).isTrue();
        assertThat(s.get("tenantId").asLong()).isEqualTo(t.getId());
        assertThat(s.get("superAdminUserId").asLong()).isEqualTo(sa.id());
        LocalDateTime ini = LocalDateTime.parse(s.get("startedAt").asText());
        assertThat(Duration.between(ini, LocalDateTime.parse(s.get("expiresAt").asText()))).isEqualTo(Duration.ofMinutes(30));
        assertThat(UUID.fromString(s.get("id").asText())).isNotNull();
        assertThat(corpo(r)).doesNotContain("token").doesNotContain("password").doesNotContain(sa.jwt());

        // identidade: o SUPER_ADMIN continua SUPER_ADMIN, sem tenant; o usuário do cliente não foi tocado
        User depois = userRepository.findById(sa.id()).orElseThrow();
        assertThat(depois.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(depois.getTenantId()).isNull();
        assertThat(depois.getTokenVersion()).isEqualTo(sa.user().getTokenVersion());

        // auditoria: ator = SUPER_ADMIN real; alvo = tenant + sessão; sem motivo, JWT nem UUID
        List<AuditEvent> ev = eventosDesde(antes);
        assertThat(ev).hasSize(1);
        AuditEvent e = ev.get(0);
        assertThat(e.getAction()).isEqualTo(AuditAction.SUPPORT_SESSION_STARTED);
        assertThat(e.getActorUserId()).isEqualTo(sa.id());
        assertThat(e.getActorRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(e.getTargetTenantId()).isEqualTo(t.getId());
        assertThat(e.getEntityType()).isEqualTo(AuditEntityType.SUPPORT_SESSION);
        assertThat(e.getMetadata()).contains("READ_ONLY");
        String dump = jdbc.queryForList("select * from audit_events where id > ?", antes).toString();
        assertThat(dump).doesNotContain(marca).doesNotContain(sa.jwt()).doesNotContain(s.get("id").asText()).doesNotContain("Bearer");
        assertThat(sessionRepository.findByPublicId(UUID.fromString(s.get("id").asText())).orElseThrow().getRequestIp()).isNotNull();
    }

    @Test
    void leituraDaAllowlist_usaOTenantDaSessao_eEAuditada() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant alvo = novoTenant("ATIVO");
        Tenant outro = novoTenant("ATIVO");
        assinatura(alvo.getId(), plano(true), SubscriptionStatus.ACTIVE);
        criarUsuario("alvo.dono@teste.local", Role.TENANT_ADMIN, alvo.getId());
        criarUsuario("outro.dono@teste.local", Role.TENANT_ADMIN, outro.getId());
        TenantBranding br = new TenantBranding();
        br.setTenantId(alvo.getId());
        br.setPrimaryColor("#112233");
        br.setUpdatedAt(LocalDateTime.now());
        brandingRepository.save(br);
        String id = iniciarOk(sa, alvo.getId());
        long antes = maxAuditId();

        JsonNode ov = json(obter(base(id) + "/tenant/overview?tenantId=" + outro.getId(), sa.jwt()));
        assertThat(ov.get("tenantId").asLong()).as("tenantId na query é ignorado").isEqualTo(alvo.getId());
        assertThat(ov.get("subscription").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(ov.get("usersTotal").asLong()).isEqualTo(1);
        assertThat(obter(base(id) + "/tenant/overview", sa.jwt()).getResponse().getContentAsString()).doesNotContain("contractedAmount").doesNotContain("invoice");

        assertThat(json(obter(base(id) + "/tenant/branding", sa.jwt())).get("primaryColor").asText()).isEqualTo("#112233");
        assertThat(json(obter(base(id) + "/tenant/entitlements", sa.jwt())).get("tenantId").asLong()).isEqualTo(alvo.getId());
        JsonNode users = json(obter(base(id) + "/tenant/users", sa.jwt()));
        assertThat(users).hasSize(1);
        assertThat(users.get(0).get("emailMasked").asText()).isEqualTo("a***@teste.local");
        assertThat(users.toString()).doesNotContain("alvo.dono").doesNotContain("outro.dono").doesNotContain("password");
        assertThat(obter(base(id) + "/tenant/errors", sa.jwt()).getResponse().getStatus()).isEqualTo(200);

        List<AuditEvent> ev = eventosDesde(antes);
        assertThat(ev).hasSize(6).allSatisfy(e -> {
            assertThat(e.getAction()).isEqualTo(AuditAction.SUPPORT_ACTION_PERFORMED);
            assertThat(e.getActorUserId()).isEqualTo(sa.id());
            assertThat(e.getTargetTenantId()).isEqualTo(alvo.getId());
            assertThat(e.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        });
        assertThat(ev.stream().map(AuditEvent::getMetadata).toList()).anyMatch(m -> m.contains("TENANT_USERS")).allMatch(m -> m.contains("READ_ONLY"));
        // as leituras não alteraram nada
        assertThat(brandingRepository.findById(alvo.getId()).orElseThrow().getPrimaryColor()).isEqualTo("#112233");
    }

    // ------------------------------------------------------------------ read-only, elevação e allowlist
    @Test
    void readOnlyBloqueiaEscrita_eEAuditadoComoNegado() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        User pendente = userRepository.save(User.builder().email("pendente.ro@teste.local").role(Role.USER).ativo(false)
                .tenantId(t.getId()).nomeCompleto("p").permissoes(new HashSet<>()).build());
        String id = iniciarOk(sa, t.getId());
        long antes = maxAuditId();
        emails.clear();
        MvcResult r = postJson(base(id) + "/tenant/users/" + pendente.getId() + "/resend-invite", Map.of(), sa.jwt());
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(emails.lastTo("pendente.ro@teste.local")).isNull();
        List<AuditEvent> ev = eventosDesde(antes);
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).getAction()).isEqualTo(AuditAction.SUPPORT_ACTION_DENIED);
        assertThat(ev.get(0).getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(ev.get(0).getMetadata()).contains("RESEND_INVITE").contains("READ_ONLY");
    }

    @Test
    void elevacaoExigeDonoConfirmacaoEMotivo_eLiberaSoOQueEstaNaAllowlist() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        SuperAdmin outroSa = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        User pendente = userRepository.save(User.builder().email("pendente.iv@teste.local").role(Role.USER).ativo(false)
                .tenantId(t.getId()).nomeCompleto("p").permissoes(new HashSet<>()).build());
        String id = iniciarOk(sa, t.getId());
        long antes = maxAuditId();

        assertThat(elevar(outroSa, id, "Tentando elevar a sessão de outro administrador", true).getResponse().getStatus()).as("outro SUPER_ADMIN").isEqualTo(403);
        assertThat(elevar(sa, id, "Corrigir convite pendente do cliente conforme chamado", false).getResponse().getStatus()).as("sem confirmação").isEqualTo(400);
        assertThat(elevar(sa, id, "Corrigir convite pendente do cliente conforme chamado", null).getResponse().getStatus()).isEqualTo(400);
        assertThat(elevar(sa, id, "curto", true).getResponse().getStatus()).as("motivo curto").isEqualTo(400);
        assertThat(elevar(sa, id, "usar password=1234 para corrigir o problema", true).getResponse().getStatus()).as("segredo").isEqualTo(400);
        assertThat(sessionRepository.findByPublicId(UUID.fromString(id)).orElseThrow().getMode()).isEqualTo(SupportMode.READ_ONLY);

        MvcResult ok = elevar(sa, id, "Corrigir convite pendente do cliente conforme chamado", true);
        assertThat(ok.getResponse().getStatus()).as(corpo(ok)).isEqualTo(200);
        assertThat(json(ok).get("mode").asText()).isEqualTo("INTERVENTION");
        assertThat(json(ok).get("elevatedAt").isNull()).isFalse();
        assertThat(elevar(sa, id, "Segunda elevação enquanto a primeira ainda vale", true).getResponse().getStatus()).as("já elevada").isEqualTo(409);

        // identidade não mudou com a elevação
        User u = userRepository.findById(sa.id()).orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(u.getTenantId()).isNull();

        // escrita da allowlist funciona e é atribuída ao SUPER_ADMIN real
        emails.clear();
        assertThat(postJson(base(id) + "/tenant/users/" + pendente.getId() + "/resend-invite", Map.of(), sa.jwt()).getResponse().getStatus()).isEqualTo(204);
        assertThat(emails.lastTo("pendente.iv@teste.local")).isNotNull();
        List<AuditEvent> ev = eventosDesde(antes);
        assertThat(ev.stream().map(AuditEvent::getAction)).contains(AuditAction.SUPPORT_SESSION_ELEVATED, AuditAction.INVITE_RESENT,
                AuditAction.SUPPORT_ACTION_PERFORMED, AuditAction.SUPPORT_ACTION_DENIED);
        assertThat(ev).allSatisfy(e -> assertThat(e.getActorUserId()).as(e.getAction().name()).isIn(sa.id(), outroSa.id()));
        assertThat(ev.stream().filter(e -> e.getAction() == AuditAction.SUPPORT_ACTION_PERFORMED).findFirst().orElseThrow().getMetadata())
                .contains("RESEND_INVITE").contains("INTERVENTION");
        assertThat(ev.stream().filter(e -> e.getAction() == AuditAction.SUPPORT_SESSION_ELEVATED).findFirst().orElseThrow().getActorUserId()).isEqualTo(sa.id());
        assertThat(jdbc.queryForList("select * from audit_events where id > ?", antes).toString()).doesNotContain("Corrigir convite").doesNotContain("password=1234");

        // usuário de OUTRO tenant: 404 (nunca vaza), mesmo em INTERVENTION
        Tenant outro = novoTenant("ATIVO");
        User deOutro = userRepository.save(User.builder().email("pendente.ot@teste.local").role(Role.USER).ativo(false)
                .tenantId(outro.getId()).nomeCompleto("p").permissoes(new HashSet<>()).build());
        assertThat(postJson(base(id) + "/tenant/users/" + deOutro.getId() + "/resend-invite", Map.of(), sa.jwt()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void intervencaoNaoLiberaNadaForaDaAllowlist_failClosed() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        String id = elevarOk(sa, iniciarOk(sa, t.getId()));
        for (String rota : List.of("/tenant/invoices", "/tenant/billing", "/tenant/subscription", "/tenant/plan", "/tenant/payments",
                "/tenant/clientes", "/tenant/branding/reset", "/tenant/anything")) {
            for (var b : List.of(get(base(id) + rota), post(base(id) + rota).contentType("application/json").content("{}"),
                    put(base(id) + rota).contentType("application/json").content("{}"), delete(base(id) + rota))) {
                assertThat(status(b, sa.jwt())).as(b.buildRequest(new org.springframework.mock.web.MockServletContext()).getMethod() + " " + rota).isEqualTo(403);
            }
        }
        // métodos de escrita nas rotas de leitura também não existem
        assertThat(status(post(base(id) + "/tenant/overview").contentType("application/json").content("{}"), sa.jwt())).isIn(403, 405);
        assertThat(status(delete(base(id) + "/tenant/users/1"), sa.jwt())).isEqualTo(403);
        // os endpoints de negócio continuam negados ao SUPER_ADMIN, com ou sem sessão/cabeçalho de suporte
        for (String rota : List.of("/api/clientes", "/api/usuarios", "/api/despesas", "/api/tarefas")) {
            assertThat(status(get(rota).header("X-Support-Session", id), sa.jwt())).as(rota).isIn(403, 404);
        }
        // e nenhum dado de billing foi tocado
        assertThat(jdbc.queryForObject("select count(*) from billing_invoices where tenant_id = ?", Long.class, t.getId())).isZero();
    }

    @Test
    void elevacaoTemporaria_voltaAReadOnlySozinha() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        User pendente = userRepository.save(User.builder().email("pendente.tmp@teste.local").role(Role.USER).ativo(false)
                .tenantId(t.getId()).nomeCompleto("p").permissoes(new HashSet<>()).build());
        String id = elevarOk(sa, iniciarOk(sa, t.getId()));
        clock.advance(Duration.ofMinutes(11));
        assertThat(json(obter(base(id), sa.jwt())).get("mode").asText()).isEqualTo("READ_ONLY");
        assertThat(obter(base(id) + "/tenant/overview", sa.jwt()).getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson(base(id) + "/tenant/users/" + pendente.getId() + "/resend-invite", Map.of(), sa.jwt()).getResponse().getStatus()).isEqualTo(403);
        assertThat(elevar(sa, id, "Nova elevação depois que a anterior expirou", true).getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson(base(id) + "/tenant/users/" + pendente.getId() + "/resend-invite", Map.of(), sa.jwt()).getResponse().getStatus()).isEqualTo(204);
    }

    // ------------------------------------------------------------------ encerramento e expiração
    @Test
    void encerrarEImediatoEIdempotente_eImpedeReuso() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        SuperAdmin outro = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        String id = iniciarOk(sa, t.getId());
        assertThat(obter(base(id) + "/tenant/overview", sa.jwt()).getResponse().getStatus()).isEqualTo(200);
        assertThat(encerrar(outro, id)).as("outro SUPER_ADMIN não encerra").isEqualTo(403);
        long antes = maxAuditId();
        assertThat(encerrar(sa, id)).isEqualTo(200);
        assertThat(encerrar(sa, id)).as("idempotente").isEqualTo(200);
        assertThat(acoesDesde(antes)).containsExactly(AuditAction.SUPPORT_SESSION_ENDED);
        assertThat(json(obter(base(id), sa.jwt())).get("active").asBoolean()).isFalse();
        assertThat(obter(base(id) + "/tenant/overview", sa.jwt()).getResponse().getStatus()).isEqualTo(403);
        assertThat(elevar(sa, id, "Tentando elevar uma sessão que já foi encerrada", true).getResponse().getStatus()).isEqualTo(409);
        assertThat(obter("/api/admin/support/sessions/current", sa.jwt()).getResponse().getStatus()).isEqualTo(404);
        assertThat(sessionRepository.findByPublicId(UUID.fromString(id)).orElseThrow().getEndedAt()).isNotNull();
        // depois de encerrar, pode abrir outra
        assertThat(iniciar(sa, t.getId(), motivo("REABRIR")).getResponse().getStatus()).isEqualTo(201);
        assertThat(encerrar(sa, UUID.randomUUID().toString())).isEqualTo(404);
        assertThat(obter(base("nao-e-uuid") + "/tenant/overview", sa.jwt()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void sessaoExpiradaNaoPodeSerUsada_eNaoBloqueiaUmaNova() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        String id = iniciarOk(sa, t.getId());
        clock.advance(Duration.ofMinutes(29));
        assertThat(obter(base(id) + "/tenant/overview", sa.jwt()).getResponse().getStatus()).as("ainda válida").isEqualTo(200);
        clock.advance(Duration.ofMinutes(2));
        assertThat(obter(base(id) + "/tenant/overview", sa.jwt()).getResponse().getStatus()).as("expirada").isEqualTo(403);
        assertThat(json(obter(base(id), sa.jwt())).get("active").asBoolean()).isFalse();
        assertThat(elevar(sa, id, "Tentando elevar uma sessão que já expirou por tempo", true).getResponse().getStatus()).isEqualTo(409);
        assertThat(obter("/api/admin/support/sessions/current", sa.jwt()).getResponse().getStatus()).isEqualTo(404);

        MvcResult nova = iniciar(sa, t.getId(), motivo("APOS-EXPIRAR"));
        assertThat(nova.getResponse().getStatus()).as(corpo(nova)).isEqualTo(201);
        SupportSession velha = sessionRepository.findByPublicId(UUID.fromString(id)).orElseThrow();
        assertThat(velha.getEndedAt()).as("a expirada foi encerrada em expires_at").isEqualTo(velha.getExpiresAt());
    }

    // ------------------------------------------------------------------ política de sessão única
    @Test
    void umaSessaoAtivaPorSuperAdmin() throws Exception {
        SuperAdmin a = novoSuperAdmin();
        SuperAdmin b = novoSuperAdmin();
        Tenant t1 = novoTenant("ATIVO");
        Tenant t2 = novoTenant("ATIVO");
        iniciarOk(a, t1.getId());
        assertThat(iniciar(a, t2.getId(), motivo("SEGUNDA")).getResponse().getStatus()).isEqualTo(409);
        assertThat(iniciar(a, t1.getId(), motivo("SEGUNDA-MESMO")).getResponse().getStatus()).isEqualTo(409);
        assertThat(iniciar(b, t2.getId(), motivo("OUTRO-SA")).getResponse().getStatus()).as("outro SUPER_ADMIN tem a própria sessão").isEqualTo(201);
        // a sessão de A continua sendo a atual e aponta para t1
        assertThat(json(obter("/api/admin/support/sessions/current", a.jwt())).get("tenantId").asLong()).isEqualTo(t1.getId());
        // uma sessão alheia não é visível nem operável
        String daB = json(obter("/api/admin/support/sessions/current", b.jwt())).get("id").asText();
        assertThat(obter(base(daB), a.jwt()).getResponse().getStatus()).isEqualTo(403);
        assertThat(obter(base(daB) + "/tenant/overview", a.jwt()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void corridaCriandoDuasSessoes_soUmaVence() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CyclicBarrier largada = new CyclicBarrier(n);
            List<Future<Boolean>> fs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int k = i;
                fs.add(pool.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            String.valueOf(sa.id()), null, com.designart.security.AuthoritiesFactory.from(sa.user())));
                    largada.await();
                    try {
                        sessionService.start(new SupportDtos.StartSessionRequest(t.getId(), motivo("CORRIDA" + k)));
                        return true;
                    } catch (com.designart.exception.ConflictException e) {
                        return false;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            int ok = 0;
            for (Future<Boolean> f : fs) {
                ok += f.get() ? 1 : 0;
            }
            assertThat(ok).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(sessionRepository.findAll().stream().filter(s -> s.getSuperAdminUserId().equals(sa.id()) && s.getEndedAt() == null)).hasSize(1);
    }

    // ------------------------------------------------------------------ tenant suspenso e isolamento
    @Test
    void tenantSuspensoRecebeSuporte_semSerReativado_eUsuariosContinuamBloqueados() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        criarUsuario("susp.dono@teste.local", Role.TENANT_ADMIN, t.getId());
        String jwtCliente = login("susp.dono@teste.local");
        assertThat(postJson("/api/admin/tenants/" + t.getId() + "/suspend", Map.of(), sa.jwt()).getResponse().getStatus()).isEqualTo(200);
        assertThat(status(get("/api/usuarios"), jwtCliente)).isIn(401, 403);

        String id = iniciarOk(sa, t.getId());
        JsonNode ov = json(obter(base(id) + "/tenant/overview", sa.jwt()));
        assertThat(ov.get("status").asText()).isEqualTo("SUSPENSO");
        assertThat(ov.get("suspensionReason").asText()).isEqualTo("MANUAL");
        assertThat(obter(base(id) + "/tenant/users", sa.jwt()).getResponse().getStatus()).isEqualTo(200);

        // a sessão não reativou o tenant: usuários continuam bloqueados (JWT antigo e login novo)
        assertThat(tenantRepository.findById(t.getId()).orElseThrow().getStatus()).isEqualTo("SUSPENSO");
        assertThat(status(get("/api/usuarios"), jwtCliente)).isIn(401, 403);
        assertThat(tentarLogin("susp.dono@teste.local", SENHA).getResponse().getStatus()).isNotEqualTo(200);
    }

    @Test
    void suporteNaoAlteraBillingNemBrandingNemEntitlements() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant t = novoTenant("ATIVO");
        assinatura(t.getId(), plano(true), SubscriptionStatus.ACTIVE);
        TenantBranding br = new TenantBranding();
        br.setTenantId(t.getId());
        br.setPrimaryColor("#AABBCC");
        br.setUpdatedAt(LocalDateTime.now());
        brandingRepository.save(br);
        String entAntes = corpo(obter("/api/admin/tenants/" + t.getId() + "/entitlements", sa.jwt()));
        String id = elevarOk(sa, iniciarOk(sa, t.getId()));
        for (String p : List.of("/tenant/overview", "/tenant/branding", "/tenant/entitlements", "/tenant/users", "/tenant/errors")) {
            assertThat(obter(base(id) + p, sa.jwt()).getResponse().getStatus()).isEqualTo(200);
        }
        assertThat(brandingRepository.findById(t.getId()).orElseThrow().getPrimaryColor()).isEqualTo("#AABBCC");
        assertThat(corpo(obter("/api/admin/tenants/" + t.getId() + "/entitlements", sa.jwt()))).isEqualTo(entAntes);
        assertThat(subscriptionRepository.findByTenantId(t.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(tenantRepository.findById(t.getId()).orElseThrow().getStatus()).isEqualTo("ATIVO");
    }

    @Test
    void historicoDeSessoesFiltraPorTenantEPagina() throws Exception {
        SuperAdmin sa = novoSuperAdmin();
        Tenant t1 = novoTenant("ATIVO");
        Tenant t2 = novoTenant("ATIVO");
        String s1 = iniciarOk(sa, t1.getId());
        encerrar(sa, s1);
        String s2 = iniciarOk(sa, t2.getId());
        JsonNode todos = json(obter("/api/admin/support/sessions?tenantId=" + t1.getId(), sa.jwt()));
        assertThat(todos.get("items")).hasSize(1);
        assertThat(todos.get("items").get(0).get("id").asText()).isEqualTo(s1);
        assertThat(todos.get("items").get(0).get("reason").asText()).contains("Investigando");
        JsonNode pag = json(obter("/api/admin/support/sessions?size=1", sa.jwt()));
        assertThat(pag.get("items")).hasSize(1);
        assertThat(pag.get("total").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(pag.get("items").get(0).get("id").asText()).as("mais recente primeiro").isEqualTo(s2);
        assertThat(obter("/api/admin/support/sessions", null).getResponse().getStatus()).isEqualTo(401);
    }
}
