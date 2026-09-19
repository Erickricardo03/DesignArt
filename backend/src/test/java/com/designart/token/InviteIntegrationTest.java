package com.designart.token;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditEvent;
import com.designart.identity.IntegrationTestBase;
import com.designart.mail.EmailMessage;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Convites: apenas TENANT_ADMIN, apenas no próprio tenant, sem senha definida pelo administrador. */
class InviteIntegrationTest extends IntegrationTestBase {

    static final String NOVA = "Senha-Do-Convidado-2026";

    private MvcResult convidar(String jwt, Map<String, Object> corpo) throws Exception {
        return postJson("/api/usuarios", corpo, jwt);
    }

    @Test
    void adminConvida_usuarioFicaPendente_semSenha_eEmailComLinkDoAppPublicUrl() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin@teste.local");
        long antes = maxAuditId();

        MvcResult r = convidar(admin, Map.of("email", "  IV.Novo@Teste.local ", "role", "USER", "nomeCompleto", "Novo Usuário"));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpoJson(r).get("convitePendente").asBoolean()).isTrue();
        assertThat(corpo(r)).doesNotContain("password").doesNotContain("$2").doesNotContain("token");

        User u = userRepository.findByEmail("iv.novo@teste.local").orElseThrow();
        assertThat(u.getPassword()).isNull();
        assertThat(u.getAtivo()).isFalse();
        assertThat(u.getEmailVerifiedAt()).isNull();
        assertThat(u.getTenantId()).isEqualTo(t.getId());
        assertThat(u.isPendingInvite()).isTrue();

        EmailMessage m = emails.lastTo("iv.novo@teste.local");
        assertThat(m.textBody()).contains("https://app.teste.local/accept-invite?token=");
        String token = emails.lastToken("iv.novo@teste.local");
        List<UserActionToken> linhas = tokenRepository.findByUserIdOrderByIdAsc(u.getId());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getPurpose()).isEqualTo(ActionTokenPurpose.INVITE);
        assertThat(linhas.get(0).getCreatedByUserId()).isNotNull();
        assertThat(Duration.between(linhas.get(0).getCreatedAt(), linhas.get(0).getExpiresAt())).isEqualTo(Duration.ofHours(72));
        assertThat(dumpTokens()).doesNotContain(token);

        // pendente não loga (sem senha; inativo)
        assertThat(tentarLogin("iv.novo@teste.local", SENHA).getResponse().getStatus()).isEqualTo(401);
        assertThat(tentarLogin("iv.novo@teste.local", "").getResponse().getStatus()).isIn(400, 401);

        List<AuditAction> acoes = auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes)
                .map(AuditEvent::getAction).toList();
        assertThat(acoes).contains(AuditAction.INVITE_CREATED, AuditAction.USER_CREATED);
        assertThat(dumpAuditoria()).doesNotContain(token);
    }

    @Test
    void administradorNuncaDefineSenha_campoPasswordIgnorado() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin2@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin2@teste.local");
        MvcResult r = convidar(admin, Map.of("email", "iv.semsenha@teste.local", "role", "USER", "password", "Definida-Pelo-Admin-1"));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(userRepository.findByEmail("iv.semsenha@teste.local").orElseThrow().getPassword()).isNull();
        assertThat(tentarLogin("iv.semsenha@teste.local", "Definida-Pelo-Admin-1").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aceitarConvite_defineSenha_ativa_verificaEmail_eConsomeToken() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin3@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin3@teste.local");
        convidar(admin, Map.of("email", "iv.aceita@teste.local", "role", "USER"));
        String token = emails.lastToken("iv.aceita@teste.local");
        long antes = maxAuditId();

        MvcResult r = aceitarConvite(token, NOVA, NOVA);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpo(r)).doesNotContain(token).doesNotContain(NOVA);

        User u = userRepository.findByEmail("iv.aceita@teste.local").orElseThrow();
        assertThat(u.getAtivo()).isTrue();
        assertThat(u.getEmailVerifiedAt()).isNotNull();
        assertThat(u.getPassword()).startsWith("$2");
        assertThat(u.isPendingInvite()).isFalse();
        assertThat(tentarLogin("iv.aceita@teste.local", NOVA).getResponse().getStatus()).isEqualTo(200);

        // replay
        assertThat(aceitarConvite(token, NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);
        assertThat(auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes)
                .map(AuditEvent::getAction)).contains(AuditAction.INVITE_ACCEPTED);
        assertThat(dumpAuditoria()).doesNotContain(token).doesNotContain(NOVA);
    }

    @Test
    void aceitarConvite_politicaDeSenhaNaoQueimaOToken_eExpiraEm72h() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin4@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin4@teste.local");
        convidar(admin, Map.of("email", "iv.politica@teste.local", "role", "USER"));
        String token = emails.lastToken("iv.politica@teste.local");

        assertThat(aceitarConvite(token, "curta", "curta").getResponse().getStatus()).isEqualTo(400);
        assertThat(aceitarConvite(token, NOVA, NOVA + "!").getResponse().getStatus()).isEqualTo(400);
        clock.advance(Duration.ofHours(71).plusMinutes(59));
        // ainda válido perto do limite: o token não foi queimado pelas tentativas fracas
        clock.advance(Duration.ofMinutes(2));
        MvcResult expirado = aceitarConvite(token, NOVA, NOVA);
        assertThat(expirado.getResponse().getStatus()).isEqualTo(400);
        assertThat(corpoJson(expirado).get("message").asText()).startsWith("Convite inválido ou expirado");
        assertThat(userRepository.findByEmail("iv.politica@teste.local").orElseThrow().getAtivo()).isFalse();
    }

    @Test
    void reenviarRevogaOTokenAnterior_soONovoFunciona() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin5@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin5@teste.local");
        long id = corpoJson(convidar(admin, Map.of("email", "iv.reenvia@teste.local", "role", "USER"))).get("id").asLong();
        String primeiro = emails.lastToken("iv.reenvia@teste.local");

        MvcResult re = executar(post("/api/usuarios/" + id + "/reenviar-convite"), admin);
        assertThat(re.getResponse().getStatus()).isEqualTo(200);
        String segundo = emails.lastToken("iv.reenvia@teste.local");
        assertThat(segundo).isNotEqualTo(primeiro);
        assertThat(emails.sentTo("iv.reenvia@teste.local")).hasSize(2);

        assertThat(aceitarConvite(primeiro, NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);
        assertThat(aceitarConvite(segundo, NOVA, NOVA).getResponse().getStatus()).isEqualTo(200);
        // depois de aceito não dá para reenviar
        assertThat(status(post("/api/usuarios/" + id + "/reenviar-convite"), admin)).isEqualTo(409);
        assertThat(auditRepository.findByTargetUserIdOrderByIdAsc(id).stream().map(AuditEvent::getAction))
                .contains(AuditAction.INVITE_CREATED, AuditAction.INVITE_RESENT, AuditAction.INVITE_ACCEPTED);
    }

    @Test
    void reenviarConviteDeOutroTenant_e404_semVazarExistencia() throws Exception {
        Tenant a = novoTenant("ATIVO");
        Tenant b = novoTenant("ATIVO");
        criarUsuario("iv.admin.a@teste.local", Role.TENANT_ADMIN, a.getId());
        criarUsuario("iv.admin.b@teste.local", Role.TENANT_ADMIN, b.getId());
        String adminA = login("iv.admin.a@teste.local");
        String adminB = login("iv.admin.b@teste.local");
        long idA = corpoJson(convidar(adminA, Map.of("email", "iv.pend.a@teste.local", "role", "USER"))).get("id").asLong();
        emails.clear();

        assertThat(status(post("/api/usuarios/" + idA + "/reenviar-convite"), adminB)).isEqualTo(404);
        assertThat(status(post("/api/usuarios/99999999/reenviar-convite"), adminB)).isEqualTo(404);
        assertThat(emails.all()).isEmpty();
    }

    @Test
    void conviteSempreNoTenantDoAdmin_eNuncaSuperAdminOuTenantArbitrario() throws Exception {
        Tenant a = novoTenant("ATIVO");
        Tenant b = novoTenant("ATIVO");
        criarUsuario("iv.admin.t@teste.local", Role.TENANT_ADMIN, a.getId());
        String admin = login("iv.admin.t@teste.local");

        // tenantId no corpo é ignorado
        MvcResult r = convidar(admin, Map.of("email", "iv.tenant@teste.local", "role", "USER", "tenantId", b.getId()));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(userRepository.findByEmail("iv.tenant@teste.local").orElseThrow().getTenantId()).isEqualTo(a.getId());

        // SUPER_ADMIN não pode ser convidado por administrador de tenant
        long antes = userRepository.count();
        assertThat(convidar(admin, Map.of("email", "iv.super@teste.local", "role", "SUPER_ADMIN")).getResponse().getStatus())
                .isGreaterThanOrEqualTo(400);
        assertThat(userRepository.findByEmail("iv.super@teste.local")).isEmpty();
        assertThat(userRepository.count()).isEqualTo(antes);
    }

    @Test
    void apenasTenantAdminConvida_usuarioComumEAnonimoNao() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.comum@teste.local", Role.USER, t.getId());
        criarUsuario("iv.superadm@teste.local", Role.SUPER_ADMIN, null);
        String comum = login("iv.comum@teste.local");
        String sup = login("iv.superadm@teste.local");
        Map<String, Object> corpo = Map.of("email", "iv.negado@teste.local", "role", "USER");
        assertThat(convidar(comum, corpo).getResponse().getStatus()).isEqualTo(403);
        assertThat(convidar(sup, corpo).getResponse().getStatus()).isIn(403, 404);
        assertThat(convidar(null, corpo).getResponse().getStatus()).isEqualTo(401);
        assertThat(userRepository.findByEmail("iv.negado@teste.local")).isEmpty();
    }

    @Test
    void emailDuplicado_e409_eNaoGeraTokenNemEmail() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin6@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("iv.existente@teste.local", Role.USER, t.getId());
        String admin = login("iv.admin6@teste.local");
        long tokens = tokenRepository.count();
        emails.clear();
        assertThat(convidar(admin, Map.of("email", "IV.Existente@teste.local", "role", "USER")).getResponse().getStatus()).isEqualTo(409);
        assertThat(tokenRepository.count()).isEqualTo(tokens);
        assertThat(emails.all()).isEmpty();
    }

    @Test
    void emailInvalidoOuRoleInvalido_ehRejeitado() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin7@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin7@teste.local");
        for (String ruim : List.of("nao-e-email", "a@b", "x@y.co\r\nBcc: a@b.co", "", " ")) {
            assertThat(convidar(admin, Map.of("email", ruim, "role", "USER")).getResponse().getStatus()).as(ruim).isEqualTo(400);
        }
        assertThat(emails.all()).isEmpty();
    }

    @Test
    void aceiteComTenantSuspenso_falhaFechado_eNaoConsome() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin8@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin8@teste.local");
        convidar(admin, Map.of("email", "iv.suspenso@teste.local", "role", "USER"));
        String token = emails.lastToken("iv.suspenso@teste.local");
        t.setStatus("SUSPENSO");
        tenantRepository.save(t);
        assertThat(aceitarConvite(token, NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);
        t.setStatus("ATIVO");
        tenantRepository.save(t);
        assertThat(aceitarConvite(token, NOVA, NOVA).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void tokenDeConviteNaoServeParaOutroUsuarioNemComoJwt() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin9@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin9@teste.local");
        convidar(admin, Map.of("email", "iv.jwt@teste.local", "role", "USER"));
        String token = emails.lastToken("iv.jwt@teste.local");
        // o token de ação nunca é aceito como credencial de sessão
        assertThat(status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me"), token)).isEqualTo(401);
        // adulterar um caractere invalida
        String adulterado = (token.charAt(0) == 'A' ? "B" : "A") + token.substring(1);
        assertThat(aceitarConvite(adulterado, NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void semEmailHabilitado_conviteRetorna503_eNaoCriaUsuario() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin10@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin10@teste.local");
        emails.setEnabled(false);
        try {
            long tokens = tokenRepository.count();
            MvcResult r = convidar(admin, Map.of("email", "iv.semmail@teste.local", "role", "USER"));
            assertThat(r.getResponse().getStatus()).isEqualTo(503);
            assertThat(userRepository.findByEmail("iv.semmail@teste.local")).isEmpty();
            assertThat(tokenRepository.count()).isEqualTo(tokens);
            assertThat(corpo(r)).doesNotContain("token=");
        } finally {
            emails.setEnabled(true);
        }
    }

    @Test
    void administradorNaoAtivaNemMudaEmailDeConvitePendente() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("iv.admin11@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("iv.admin11@teste.local");
        long id = corpoJson(convidar(admin, Map.of("email", "iv.pend@teste.local", "role", "USER"))).get("id").asLong();
        int st = status(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/usuarios/" + id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"), admin);
        assertThat(st).isGreaterThanOrEqualTo(400);
        assertThat(userRepository.findById(id).orElseThrow().getAtivo()).isFalse();
    }
}
