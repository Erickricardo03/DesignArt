package com.designart.admin;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditEvent;
import com.designart.billing.Plan;
import com.designart.billing.Subscription;
import com.designart.billing.SubscriptionStatus;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import com.designart.token.UserActionToken;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Criação/suspensão de empresas, convite do TENANT_ADMIN e independência assinatura × tenant. */
class AdminTenantIntegrationTest extends AdminTestBase {

    static final String SENHA_CLIENTE = "Senha-Do-Cliente-2026";

    private Map<String, Object> corpo(String slug, String planCode, String adminEmail) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Empresa " + slug);
        m.put("slug", slug);
        m.put("planCode", planCode);
        m.put("adminEmail", adminEmail);
        m.put("adminNome", "Admin " + slug);
        return m;
    }

    private List<AuditAction> acoesDesde(long antes) {
        return auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes)
                .map(AuditEvent::getAction).toList();
    }

    @Test
    void superAdminCriaEmpresa_assinatura_adminPendente_conviteSeguro_eClienteDefineASenha() throws Exception {
        String sup = superJwt();
        Plan plano = plano(true);
        long antes = maxAuditId();
        long tenantsAntes = tenantRepository.count();

        Map<String, Object> req = corpo("emp-" + uniq("a"), plano.getCode(), "  Dono.Um@Teste.local ");
        req.put("password", "Senha-Definida-Pelo-Super-1");   // tentativa: SUPER_ADMIN definir senha (ignorado)
        req.put("adminPassword", "Senha-Definida-Pelo-Super-2");
        MvcResult r = postJson("/api/admin/tenants", req, sup);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        var body = corpoJson(r);
        long tenantId = body.get("tenant").get("id").asLong();
        assertThat(body.get("tenant").get("status").asText()).isEqualTo("ATIVO");
        assertThat(body.get("subscription").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.get("subscription").get("planCode").asText()).isEqualTo(plano.getCode());
        assertThat(body.get("admin").get("convitePendente").asBoolean()).isTrue();
        assertThat(body.get("admin").get("role").asText()).isEqualTo("TENANT_ADMIN");
        assertThat(corpo(r)).doesNotContain("token").doesNotContain("password").doesNotContain("$2").doesNotContain("Senha-Definida");
        assertThat(tenantRepository.count()).isEqualTo(tenantsAntes + 1);

        User admin = userRepository.findByEmail("dono.um@teste.local").orElseThrow();
        assertThat(admin.getPassword()).as("o SUPER_ADMIN nunca define a senha").isNull();
        assertThat(admin.getAtivo()).isFalse();
        assertThat(admin.getRole()).isEqualTo(Role.TENANT_ADMIN);
        assertThat(admin.getTenantId()).isEqualTo(tenantId);
        assertThat(tentarLogin("dono.um@teste.local", "Senha-Definida-Pelo-Super-1").getResponse().getStatus()).isEqualTo(401);

        // convite: e-mail capturado com link do APP_PUBLIC_URL; o cliente cria a própria senha
        assertThat(emails.lastTo("dono.um@teste.local").textBody()).contains("https://app.teste.local/accept-invite?token=");
        String token = emails.lastToken("dono.um@teste.local");
        List<UserActionToken> linhas = tokenRepository.findByUserIdOrderByIdAsc(admin.getId());
        assertThat(linhas).hasSize(1);
        assertThat(dumpTokens()).doesNotContain(token);
        assertThat(aceitarConvite(token, SENHA_CLIENTE, SENHA_CLIENTE).getResponse().getStatus()).isEqualTo(200);
        String jwtCliente = "";
        MvcResult login = tentarLogin("dono.um@teste.local", SENHA_CLIENTE);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        jwtCliente = corpoJson(login).get("token").asText();
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/clientes"), jwtCliente)).isEqualTo(200);
        // o cliente não acessa /api/admin
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/tenants"), jwtCliente)).isEqualTo(403);

        // auditoria: eventos novos + os de convite existentes, sem duplicar/enfraquecer, sem segredos
        List<AuditAction> acoes = acoesDesde(antes);
        assertThat(acoes).contains(AuditAction.TENANT_CREATED, AuditAction.SUBSCRIPTION_CREATED, AuditAction.USER_CREATED,
                AuditAction.INVITE_CREATED, AuditAction.INVITE_ACCEPTED);
        assertThat(acoes.stream().filter(a -> a == AuditAction.INVITE_CREATED).count()).isEqualTo(1);
        assertThat(acoes.stream().filter(a -> a == AuditAction.TENANT_CREATED).count()).isEqualTo(1);
        AuditEvent criado = auditRepository.findAllByOrderByIdAsc().stream()
                .filter(e -> e.getId() > antes && e.getAction() == AuditAction.TENANT_CREATED).findFirst().orElseThrow();
        assertThat(criado.getActorRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(criado.getTargetTenantId()).isEqualTo(tenantId);
        assertThat(dumpAuditoria()).doesNotContain(token).doesNotContain(SENHA_CLIENTE).doesNotContain("Senha-Definida");
    }

    @Test
    void validacoesEConflitos_naoCriamNada() throws Exception {
        String sup = superJwt();
        Plan ativo = plano(true);
        Plan inativo = plano(false);
        String slugExistente = "emp-" + uniq("dup");
        assertThat(postJson("/api/admin/tenants", corpo(slugExistente, ativo.getCode(), "v1@teste.local"), sup)
                .getResponse().getStatus()).isEqualTo(201);
        long tenants = tenantRepository.count();
        long users = userRepository.count();
        long tokens = tokenRepository.count();
        emails.clear();

        // slug duplicado
        assertThat(postJson("/api/admin/tenants", corpo(slugExistente, ativo.getCode(), "v2@teste.local"), sup).getResponse().getStatus()).isEqualTo(409);
        // e-mail do admin duplicado: a transação inteira é desfeita (nenhum tenant órfão)
        assertThat(postJson("/api/admin/tenants", corpo("emp-" + uniq("x"), ativo.getCode(), "V1@teste.local"), sup).getResponse().getStatus()).isEqualTo(409);
        // plano inexistente / inativo
        assertThat(postJson("/api/admin/tenants", corpo("emp-" + uniq("y"), "NAO_EXISTE", "v3@teste.local"), sup).getResponse().getStatus()).isEqualTo(400);
        assertThat(postJson("/api/admin/tenants", corpo("emp-" + uniq("z"), inativo.getCode(), "v4@teste.local"), sup).getResponse().getStatus()).isEqualTo(400);
        // entradas inválidas
        for (String slug : List.of("SLUG MAIUSCULO", "-comeca", "termina-", "a", "com_underscore", "x".repeat(60), "")) {
            assertThat(postJson("/api/admin/tenants", corpo(slug, ativo.getCode(), "v5@teste.local"), sup).getResponse().getStatus()).as(slug).isEqualTo(400);
        }
        assertThat(postJson("/api/admin/tenants", corpo("emp-" + uniq("w"), ativo.getCode(), "nao-e-email"), sup).getResponse().getStatus()).isEqualTo(400);
        Map<String, Object> semNome = corpo("emp-" + uniq("n"), ativo.getCode(), "v6@teste.local");
        semNome.put("name", " ");
        assertThat(postJson("/api/admin/tenants", semNome, sup).getResponse().getStatus()).isEqualTo(400);
        Map<String, Object> passado = corpo("emp-" + uniq("p"), ativo.getCode(), "v7@teste.local");
        passado.put("currentPeriodEnd", LocalDateTime.now().minusDays(1).toString());
        assertThat(postJson("/api/admin/tenants", passado, sup).getResponse().getStatus()).isEqualTo(400);

        assertThat(tenantRepository.count()).isEqualTo(tenants);
        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(tokenRepository.count()).isEqualTo(tokens);
        assertThat(emails.all()).isEmpty();
    }

    @Test
    void semEmailHabilitado_criacaoRetorna503_eNadaEhCriado() throws Exception {
        String sup = superJwt();
        Plan plano = plano(true);
        long tenants = tenantRepository.count();
        emails.setEnabled(false);
        MvcResult r = postJson("/api/admin/tenants", corpo("emp-" + uniq("off"), plano.getCode(), "off@teste.local"), sup);
        assertThat(r.getResponse().getStatus()).isEqualTo(503);
        assertThat(tenantRepository.count()).isEqualTo(tenants);
        assertThat(userRepository.findByEmail("off@teste.local")).isEmpty();
    }

    @Test
    void falhaDeEnvioDoConvite_revogaTokenEAudita_empresaPermaneceEConvitePodeSerReenviado() throws Exception {
        String sup = superJwt();
        Plan plano = plano(true);
        long antes = maxAuditId();
        emails.setFailing(true);
        MvcResult r = postJson("/api/admin/tenants", corpo("emp-" + uniq("f"), plano.getCode(), "falha.adm@teste.local"), sup);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        long tenantId = corpoJson(r).get("tenant").get("id").asLong();
        long adminId = corpoJson(r).get("admin").get("id").asLong();
        assertThat(emails.all()).isEmpty();
        List<UserActionToken> linhas = tokenRepository.findByUserIdOrderByIdAsc(adminId);
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getRevokedAt()).as("nenhum token válido sem e-mail").isNotNull();
        assertThat(acoesDesde(antes)).contains(AuditAction.INVITE_EMAIL_FAILED);

        emails.setFailing(false);
        MvcResult re = executar(post("/api/admin/tenants/" + tenantId + "/users/" + adminId + "/resend-invite"), sup);
        assertThat(re.getResponse().getStatus()).isEqualTo(200);
        String token = emails.lastToken("falha.adm@teste.local");
        assertThat(aceitarConvite(token, SENHA_CLIENTE, SENHA_CLIENTE).getResponse().getStatus()).isEqualTo(200);
        // reenviar depois de aceito: 409; para usuário de OUTRO tenant: 404
        assertThat(status(post("/api/admin/tenants/" + tenantId + "/users/" + adminId + "/resend-invite"), sup)).isEqualTo(409);
        Tenant outro = novoTenant("ATIVO");
        assertThat(status(post("/api/admin/tenants/" + outro.getId() + "/users/" + adminId + "/resend-invite"), sup)).isEqualTo(404);
    }

    @Test
    void suspenderBloqueiaOsUsuariosDoTenant_semTocarNaAssinatura_eReativarRestaura() throws Exception {
        String sup = superJwt();
        Plan plano = plano(true);
        Tenant t = novoTenant("ATIVO");
        Subscription sub = assinatura(t.getId(), plano, SubscriptionStatus.ACTIVE);
        criarUsuario("sp.user@teste.local", Role.USER, t.getId());
        criarUsuario("sp.adm@teste.local", Role.TENANT_ADMIN, t.getId());
        String jwtUser = login("sp.user@teste.local");
        String jwtAdm = login("sp.adm@teste.local");
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/clientes"), jwtAdm)).isEqualTo(200);
        long antes = maxAuditId();

        MvcResult s = executar(post("/api/admin/tenants/" + t.getId() + "/suspend"), sup);
        assertThat(s.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpoJson(s).get("status").asText()).isEqualTo("SUSPENSO");
        // usuários do tenant suspenso: JWT existente e novo login negados; dados preservados
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/clientes"), jwtAdm)).isEqualTo(401);
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me"), jwtUser)).isEqualTo(401);
        assertThat(tentarLogin("sp.adm@teste.local", SENHA).getResponse().getStatus()).isEqualTo(401);
        assertThat(userRepository.findByEmail("sp.adm@teste.local")).isPresent();
        // a assinatura NÃO mudou automaticamente
        assertThat(subscriptionRepository.findById(sub.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        // suspender duas vezes: 409
        assertThat(status(post("/api/admin/tenants/" + t.getId() + "/suspend"), sup)).isEqualTo(409);

        MvcResult re = executar(post("/api/admin/tenants/" + t.getId() + "/reactivate"), sup);
        assertThat(corpoJson(re).get("status").asText()).isEqualTo("ATIVO");
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/clientes"), jwtAdm)).isEqualTo(200);
        assertThat(tentarLogin("sp.adm@teste.local", SENHA).getResponse().getStatus()).isEqualTo(200);
        assertThat(status(post("/api/admin/tenants/" + t.getId() + "/reactivate"), sup)).isEqualTo(409);

        assertThat(acoesDesde(antes)).contains(AuditAction.TENANT_SUSPENDED, AuditAction.TENANT_REACTIVATED);
        AuditEvent ev = auditRepository.findAllByOrderByIdAsc().stream()
                .filter(e -> e.getId() > antes && e.getAction() == AuditAction.TENANT_SUSPENDED).findFirst().orElseThrow();
        assertThat(ev.getMetadata()).contains("ATIVO").contains("SUSPENSO");
    }

    @Test
    void statusDaAssinaturaNaoAlteraOStatusDoTenant_eViceVersa() throws Exception {
        String sup = superJwt();
        Plan plano = plano(true);
        Tenant t = novoTenant("ATIVO");
        criarUsuario("ind.adm@teste.local", Role.TENANT_ADMIN, t.getId());
        String jwt = login("ind.adm@teste.local");
        assertThat(putJson("/api/admin/tenants/" + t.getId() + "/subscription",
                Map.of("planCode", plano.getCode(), "status", "ACTIVE"), sup).getResponse().getStatus()).isEqualTo(200);

        for (String st : List.of("PAST_DUE", "CANCELED", "ACTIVE")) {
            MvcResult r = putJson("/api/admin/tenants/" + t.getId() + "/subscription",
                    Map.of("planCode", plano.getCode(), "status", st), sup);
            assertThat(r.getResponse().getStatus()).as(st).isEqualTo(200);
            assertThat(corpoJson(r).get("status").asText()).isEqualTo(st);
            assertThat(corpoJson(r).get("canceledAt").isNull()).isEqualTo(!st.equals("CANCELED"));
            // tenant continua ATIVO e o usuário continua operando, seja qual for a situação comercial
            assertThat(tenantRepository.findById(t.getId()).orElseThrow().getStatus()).isEqualTo("ATIVO");
            assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/clientes"), jwt)).isEqualTo(200);
        }
        // suspender o tenant não muda a assinatura
        executar(post("/api/admin/tenants/" + t.getId() + "/suspend"), sup);
        assertThat(subscriptionRepository.findByTenantId(t.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void consultasEListagem() throws Exception {
        String sup = superJwt();
        Tenant t = novoTenant("ATIVO");
        assertThat(corpoJson(obter("/api/admin/tenants/" + t.getId(), sup)).get("slug").asText()).isEqualTo(t.getSlug());
        assertThat(codigos(corpoJson(obter("/api/admin/tenants?page=0&size=1000", sup)), "slug")).isNotEmpty();
        assertThat(obter("/api/admin/tenants/99999999", sup).getResponse().getStatus()).isEqualTo(404);
        assertThat(obter("/api/admin/tenants/" + t.getId() + "/subscription", sup).getResponse().getStatus()).as("sem assinatura").isEqualTo(404);
        assertThat(obter("/api/admin/tenants/99999999/entitlements", sup).getResponse().getStatus()).isEqualTo(404);
        assertThat(postJson("/api/admin/tenants/99999999/suspend", Map.of(), sup).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void naoHaExclusaoFisicaDeTenant() throws Exception {
        String sup = superJwt();
        Tenant t = novoTenant("ATIVO");
        int s = status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/tenants/" + t.getId()), sup);
        assertThat(s).isIn(404, 405);
        assertThat(tenantRepository.findById(t.getId())).isPresent();
    }
}
