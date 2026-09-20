package com.designart.audit;

import com.designart.identity.IntegrationTestBase;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Permission;
import com.designart.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Eventos de auditoria gerados pelos fluxos que já existem (login e gestão de usuários). */
class AuditIntegrationTest extends IntegrationTestBase {

    // ------------------------------------------------------------------ helpers
    private RequestPostProcessor ip(String remoteAddr) {
        return r -> {
            r.setRemoteAddr(remoteAddr);
            return r;
        };
    }

    private List<AuditEvent> depois(long antes) {
        return auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes).toList();
    }

    private AuditEvent unico(List<AuditEvent> eventos, AuditAction acao) {
        List<AuditEvent> f = eventos.stream().filter(e -> e.getAction() == acao).toList();
        assertThat(f).as("eventos " + acao + " em " + eventos.stream().map(AuditEvent::getAction).toList()).hasSize(1);
        return f.get(0);
    }

    private long contar(List<AuditEvent> eventos, AuditAction acao) {
        return eventos.stream().filter(e -> e.getAction() == acao).count();
    }

    private JsonNode meta(AuditEvent e) throws Exception {
        assertThat(e.getMetadata()).as("metadata de " + e.getAction()).isNotNull();
        return json.readTree(e.getMetadata());
    }

    private MvcResult put(Long id, String token, Map<String, Object> corpo) throws Exception {
        return executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/usuarios/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)), token);
    }

    private MvcResult postUsuario(String token, Map<String, Object> corpo) throws Exception {
        return executar(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)), token);
    }

    private List<String> nomes(JsonNode array) {
        List<String> l = new ArrayList<>();
        array.forEach(n -> l.add(n.asText()));
        return l;
    }

    // ------------------------------------------------------------------ login
    @Test
    void loginBemSucedidoGeraLoginSuccess_comAtorAlvoTenantEContexto() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("aud.login.ok@teste.local", Role.USER, t.getId());
        long antes = maxAuditId();

        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "AUD.Login.OK@teste.local ", "password", SENHA)))
                .header("User-Agent", "TesteUA/1.0 (auditoria)").with(ip("203.0.113.7"))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);

        AuditEvent e = unico(depois(antes), AuditAction.LOGIN_SUCCESS);
        assertThat(e.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(e.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(e.getActorUserId()).isEqualTo(u.getId());
        assertThat(e.getActorEmail()).isEqualTo("aud.login.ok@teste.local"); // snapshot normalizado, do banco
        assertThat(e.getActorRole()).isEqualTo(Role.USER);
        assertThat(e.getActorTenantId()).isEqualTo(t.getId());
        assertThat(e.getTargetUserId()).isEqualTo(u.getId());
        assertThat(e.getTargetTenantId()).isEqualTo(t.getId());
        assertThat(e.getEntityType()).isEqualTo(AuditEntityType.USER);
        assertThat(e.getEntityId()).isEqualTo(u.getId());
        assertThat(e.getRequestIp()).isEqualTo("203.0.113.7");
        assertThat(e.getUserAgent()).isEqualTo("TesteUA/1.0 (auditoria)");
        assertThat(e.getMetadata()).isNull();
        assertThat(e.getOccurredAt()).isNotNull();
    }

    @Test
    void superAdminLoginGeraEventoSemTenant() throws Exception {
        User s = criarUsuario("aud.super@teste.local", Role.SUPER_ADMIN, null);
        long antes = maxAuditId();
        login("aud.super@teste.local");
        AuditEvent e = unico(depois(antes), AuditAction.LOGIN_SUCCESS);
        assertThat(e.getActorRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(e.getActorTenantId()).isNull();
        assertThat(e.getTargetTenantId()).isNull();
        assertThat(e.getActorUserId()).isEqualTo(s.getId());
    }

    @Test
    void loginFalhoComContaDesconhecida_naoArmazenaOTextoDigitado() throws Exception {
        // O usuário digitou a SENHA no campo de e-mail (erro comum). Nada disso pode ir para a auditoria.
        String senhaNoCampoEmail = "MinhaSenhaSecreta-Digitada-No-Email-987!";
        String emailInexistente = "quem.digitou.isto@teste.local";
        long antes = maxAuditId();

        for (String entrada : List.of(senhaNoCampoEmail, emailInexistente, "isto nao e email")) {
            MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", entrada, "password", "outra-Senha-Digitada-555")))
                    .header("User-Agent", "TesteUA/2.0").with(ip("198.51.100.20"))).andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
        }

        List<AuditEvent> novos = depois(antes);
        assertThat(novos).hasSize(3);
        for (AuditEvent e : novos) {
            assertThat(e.getAction()).isEqualTo(AuditAction.LOGIN_FAILURE);
            assertThat(e.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
            assertThat(e.getActorType()).isEqualTo(AuditActorType.ANONYMOUS);
            assertThat(e.getActorUserId()).isNull();
            assertThat(e.getActorEmail()).isNull();
            assertThat(e.getTargetUserId()).isNull();
            assertThat(e.getTargetEmail()).isNull();
            assertThat(e.getTargetTenantId()).isNull();
            assertThat(nomes(meta(e).get("reasons"))).containsExactly("UNKNOWN_ACCOUNT");
            assertThat(e.getRequestIp()).isEqualTo("198.51.100.20");
        }
        String tabela = dumpAuditoria();
        assertThat(tabela).doesNotContain(senhaNoCampoEmail).doesNotContain("MinhaSenhaSecreta")
                .doesNotContain(emailInexistente).doesNotContain("outra-Senha-Digitada-555")
                .doesNotContain("isto nao e email");
    }

    @Test
    void loginFalhoComContaExistente_alvoVemDoBancoEMotivoEhCategoria() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("aud.falha.existente@teste.local", Role.USER, t.getId());
        long antes = maxAuditId();
        assertThat(tentarLogin("aud.falha.existente@teste.local", "Senha-Errada-Marcador-321").getResponse().getStatus()).isEqualTo(401);

        AuditEvent e = unico(depois(antes), AuditAction.LOGIN_FAILURE);
        assertThat(e.getActorType()).isEqualTo(AuditActorType.ANONYMOUS);
        assertThat(e.getTargetUserId()).isEqualTo(u.getId());
        assertThat(e.getTargetEmail()).isEqualTo("aud.falha.existente@teste.local");
        assertThat(e.getTargetTenantId()).isEqualTo(t.getId());
        assertThat(nomes(meta(e).get("reasons"))).containsExactly("BAD_CREDENTIALS");
        assertThat(dumpAuditoria()).doesNotContain("Senha-Errada-Marcador-321");
    }

    @Test
    void loginNegadoPorUsuarioInativoOuTenantSuspenso_geraAccessDenied_semMudarARespostaGenerica() throws Exception {
        Tenant ativo = novoTenant("ATIVO");
        Tenant suspenso = novoTenant("SUSPENSO");
        criarUsuario("aud.inativo@teste.local", Role.USER, ativo.getId(), false, SENHA);
        criarUsuario("aud.suspenso@teste.local", Role.USER, suspenso.getId());
        long antes = maxAuditId();

        MvcResult a = tentarLogin("aud.inativo@teste.local", SENHA);
        MvcResult b = tentarLogin("aud.suspenso@teste.local", SENHA);
        assertThat(corpoJson(a).get("message").asText()).isEqualTo("Usuário ou senha inválidos.");
        assertThat(corpoJson(b).get("message").asText()).isEqualTo("Usuário ou senha inválidos.");

        List<AuditEvent> novos = depois(antes);
        assertThat(novos).hasSize(2);
        for (AuditEvent e : novos) {
            assertThat(e.getAction()).isEqualTo(AuditAction.LOGIN_FAILURE);
            assertThat(nomes(meta(e).get("reasons"))).containsExactly("ACCESS_DENIED");
        }
    }

    // ------------------------------------------------------------------ usuários
    @Test
    void criarUsuarioGeraUserCreated_comAtorAlvoTenantESemSegredos() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User admin = criarUsuario("aud.admin.cria@teste.local", Role.TENANT_ADMIN, t.getId());
        String token = login("aud.admin.cria@teste.local");
        long antes = maxAuditId();

        MvcResult r = postUsuario(token, Map.of("email", "Novo.Criado@Teste.Local",
                "role", "USER", "permissoes", List.of("FINANCEIRO")));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        long novoId = corpoJson(r).get("id").asLong();
        String tokenConvite = emails.lastToken("novo.criado@teste.local");
        assertThat(userRepository.findById(novoId).orElseThrow().getPassword()).isNull(); // o administrador não define senha

        AuditEvent e = unico(depois(antes), AuditAction.USER_CREATED);
        assertThat(e.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(e.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(e.getActorUserId()).isEqualTo(admin.getId());
        assertThat(e.getActorEmail()).isEqualTo("aud.admin.cria@teste.local");
        assertThat(e.getActorRole()).isEqualTo(Role.TENANT_ADMIN);
        assertThat(e.getActorTenantId()).isEqualTo(t.getId());
        assertThat(e.getTargetUserId()).isEqualTo(novoId);
        assertThat(e.getTargetEmail()).isEqualTo("novo.criado@teste.local");
        assertThat(e.getTargetTenantId()).isEqualTo(t.getId());
        JsonNode m = meta(e);
        assertThat(m.get("roleTo").asText()).isEqualTo("USER");
        assertThat(nomes(m.get("permissions"))).containsExactly("FINANCEIRO");
        String linha = e.getMetadata() + e.getActorEmail() + e.getTargetEmail() + e.getUserAgent();
        assertThat(linha).doesNotContain(tokenConvite);
        assertThat(dumpAuditoria()).doesNotContain(tokenConvite);
        // O convite também é auditado (mesma transação), sem o token.
        assertThat(contar(depois(antes), AuditAction.INVITE_CREATED)).isEqualTo(1);
    }

    @Test
    void alterarRoleGeraUserRoleChanged_eSessionsRevoked() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User admin = criarUsuario("aud.admin.role@teste.local", Role.TENANT_ADMIN, t.getId());
        User alvo = criarUsuario("aud.alvo.role@teste.local", Role.USER, t.getId());
        String token = login("aud.admin.role@teste.local");
        long antes = maxAuditId();

        assertThat(put(alvo.getId(), token, Map.of("role", "TENANT_ADMIN")).getResponse().getStatus()).isEqualTo(200);

        List<AuditEvent> novos = depois(antes);
        AuditEvent role = unico(novos, AuditAction.USER_ROLE_CHANGED);
        assertThat(role.getActorUserId()).isEqualTo(admin.getId());
        assertThat(role.getTargetUserId()).isEqualTo(alvo.getId());
        assertThat(role.getTargetTenantId()).isEqualTo(t.getId());
        assertThat(meta(role).get("roleFrom").asText()).isEqualTo("USER");
        assertThat(meta(role).get("roleTo").asText()).isEqualTo("TENANT_ADMIN");
        AuditEvent revogacao = unico(novos, AuditAction.SESSIONS_REVOKED);
        assertThat(nomes(meta(revogacao).get("reasons"))).containsExactly("ROLE_CHANGED");
        assertThat(contar(novos, AuditAction.USER_PERMISSIONS_CHANGED)).isZero();
        assertThat(contar(novos, AuditAction.USER_UPDATED)).isZero();
    }

    @Test
    void alterarPermissoesGeraUserPermissionsChanged_semRevogarSessoes() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.admin.perm@teste.local", Role.TENANT_ADMIN, t.getId());
        User alvo = criarUsuario("aud.alvo.perm@teste.local", Role.USER, t.getId());
        String token = login("aud.admin.perm@teste.local");

        long antes = maxAuditId();
        assertThat(put(alvo.getId(), token, Map.of("permissoes", List.of("FINANCEIRO", "EQUIPE"))).getResponse().getStatus()).isEqualTo(200);
        List<AuditEvent> primeiro = depois(antes);
        AuditEvent e1 = unico(primeiro, AuditAction.USER_PERMISSIONS_CHANGED);
        assertThat(nomes(meta(e1).get("permissionsAdded"))).containsExactly("EQUIPE", "FINANCEIRO");
        assertThat(nomes(meta(e1).get("permissionsRemoved"))).isEmpty();
        assertThat(contar(primeiro, AuditAction.SESSIONS_REVOKED)).isZero(); // authorities vêm do banco a cada requisição

        long antes2 = maxAuditId();
        assertThat(put(alvo.getId(), token, Map.of("permissoes", List.of("EQUIPE"))).getResponse().getStatus()).isEqualTo(200);
        AuditEvent e2 = unico(depois(antes2), AuditAction.USER_PERMISSIONS_CHANGED);
        assertThat(nomes(meta(e2).get("permissionsAdded"))).isEmpty();
        assertThat(nomes(meta(e2).get("permissionsRemoved"))).containsExactly("FINANCEIRO");

        // Reenviar as mesmas permissões não gera evento.
        long antes3 = maxAuditId();
        assertThat(put(alvo.getId(), token, Map.of("permissoes", List.of("EQUIPE"))).getResponse().getStatus()).isEqualTo(200);
        assertThat(depois(antes3)).isEmpty();
    }

    @Test
    void desativarEAtivarGeramEventos_eDesativacaoRevogaSessoes() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.admin.ativo@teste.local", Role.TENANT_ADMIN, t.getId());
        User alvo = criarUsuario("aud.alvo.ativo@teste.local", Role.USER, t.getId());
        String token = login("aud.admin.ativo@teste.local");

        long antes = maxAuditId();
        assertThat(put(alvo.getId(), token, Map.of("ativo", false)).getResponse().getStatus()).isEqualTo(200);
        List<AuditEvent> desativacao = depois(antes);
        AuditEvent d = unico(desativacao, AuditAction.USER_DEACTIVATED);
        assertThat(meta(d).get("activeFrom").asBoolean()).isTrue();
        assertThat(meta(d).get("activeTo").asBoolean()).isFalse();
        assertThat(nomes(meta(unico(desativacao, AuditAction.SESSIONS_REVOKED)).get("reasons"))).containsExactly("DEACTIVATED");

        long antes2 = maxAuditId();
        assertThat(put(alvo.getId(), token, Map.of("ativo", true)).getResponse().getStatus()).isEqualTo(200);
        List<AuditEvent> ativacao = depois(antes2);
        AuditEvent a = unico(ativacao, AuditAction.USER_ACTIVATED);
        assertThat(meta(a).get("activeFrom").asBoolean()).isFalse();
        assertThat(meta(a).get("activeTo").asBoolean()).isTrue();
        assertThat(contar(ativacao, AuditAction.SESSIONS_REVOKED)).isZero();
    }

    @Test
    void alterarEmailNomeCargo_auditaSomenteOsNomesDosCampos() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.admin.campos@teste.local", Role.TENANT_ADMIN, t.getId());
        User alvo = criarUsuario("aud.alvo.campos@teste.local", Role.USER, t.getId());
        String token = login("aud.admin.campos@teste.local");
        long antes = maxAuditId();

        assertThat(put(alvo.getId(), token, Map.of("email", "Alvo.Novo.Email@Teste.Local", "nomeCompleto", "Nome Novo Marcador",
                "cargo", "Cargo Novo Marcador")).getResponse().getStatus()).isEqualTo(200);

        List<AuditEvent> novos = depois(antes);
        AuditEvent upd = unico(novos, AuditAction.USER_UPDATED);
        assertThat(nomes(meta(upd).get("fields"))).containsExactly("CARGO", "EMAIL", "NOME");
        assertThat(upd.getTargetEmail()).isEqualTo("alvo.novo.email@teste.local"); // snapshot do estado no momento
        assertThat(nomes(meta(unico(novos, AuditAction.SESSIONS_REVOKED)).get("reasons")))
                .containsExactly("EMAIL_CHANGED");
        String tabela = dumpAuditoria();
        assertThat(tabela).doesNotContain("Nome Novo Marcador")
                .doesNotContain("Cargo Novo Marcador");
        // Só a chave "fields" existe: nomes e valores do que mudou não vão para o metadata.
        assertThat(upd.getMetadata()).doesNotContain("Marcador");
    }

    @Test
    void atualizacaoSemMudancaRealNaoGeraEventos() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.admin.igual@teste.local", Role.TENANT_ADMIN, t.getId());
        User alvo = criarUsuario("aud.alvo.igual@teste.local", Role.USER, t.getId());
        String token = login("aud.admin.igual@teste.local");
        long antes = maxAuditId();
        assertThat(put(alvo.getId(), token, Map.of("email", "aud.alvo.igual@teste.local", "nomeCompleto", "aud.alvo.igual@teste.local",
                "role", "USER", "ativo", true)).getResponse().getStatus()).isEqualTo(200);
        assertThat(depois(antes)).isEmpty();
    }

    @Test
    void removerUsuarioGeraUserDeleted_eOHistoricoSobreviveAoUsuario() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.admin.del@teste.local", Role.TENANT_ADMIN, t.getId());
        String token = login("aud.admin.del@teste.local");
        MvcResult criado = postUsuario(token, Map.of("email", "aud.sera.removido@teste.local", "password", "Senha-Valida-123456"));
        long alvoId = corpoJson(criado).get("id").asLong();
        assertThat(executar(delete("/api/usuarios/" + alvoId), token).getResponse().getStatus()).isEqualTo(204);

        assertThat(userRepository.findById(alvoId)).isEmpty(); // o usuário não existe mais...
        List<AuditEvent> historico = auditRepository.findByTargetUserIdOrderByIdAsc(alvoId);
        assertThat(historico.stream().map(AuditEvent::getAction).toList())
                .containsExactly(AuditAction.USER_CREATED, AuditAction.INVITE_CREATED, AuditAction.USER_DELETED); // ...mas a trilha continua inteira
        for (AuditEvent e : historico) {
            assertThat(e.getTargetEmail()).isEqualTo("aud.sera.removido@teste.local"); // snapshot preservado
            assertThat(e.getTargetTenantId()).isEqualTo(t.getId());
            assertThat(e.getActorEmail()).isEqualTo("aud.admin.del@teste.local");
        }
    }

    @Test
    void snapshotsPermanecemDepoisDeMudancasPosterioresNoAtorEAlvo() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User admin = criarUsuario("aud.snap.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("aud.snap.admin2@teste.local", Role.TENANT_ADMIN, t.getId()); // para permitir rebaixar o primeiro
        String token = login("aud.snap.admin@teste.local");
        User alvo = criarUsuario("aud.snap.alvo@teste.local", Role.USER, t.getId());
        long antes = maxAuditId();
        assertThat(put(alvo.getId(), token, Map.of("cargo", "Cargo Original")).getResponse().getStatus()).isEqualTo(200);
        AuditEvent original = unico(depois(antes), AuditAction.USER_UPDATED);
        Long idEvento = original.getId();

        // Depois: o ator muda de e-mail, é rebaixado e desativado; o alvo muda de e-mail.
        User a = userRepository.findById(admin.getId()).orElseThrow();
        a.setEmail("aud.snap.admin.NOVO@teste.local");
        a.setRole(Role.USER);
        a.setAtivo(false);
        userRepository.save(a);
        User b = userRepository.findById(alvo.getId()).orElseThrow();
        b.setEmail("aud.snap.alvo.NOVO@teste.local");
        userRepository.save(b);

        AuditEvent relido = auditRepository.findAllByOrderByIdAsc().stream()
                .filter(e -> e.getId().equals(idEvento)).findFirst().orElseThrow();
        assertThat(relido.getActorEmail()).isEqualTo("aud.snap.admin@teste.local");
        assertThat(relido.getActorRole()).isEqualTo(Role.TENANT_ADMIN);
        assertThat(relido.getActorTenantId()).isEqualTo(t.getId());
        assertThat(relido.getTargetEmail()).isEqualTo("aud.snap.alvo@teste.local");
    }

    @Test
    void operacoesNegadasOuInvalidasNaoGeramEventosDeSucesso() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.admin.neg@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("aud.user.neg@teste.local", Role.USER, t.getId());
        User existente = criarUsuario("aud.existente@teste.local", Role.USER, t.getId());
        String tokenAdmin = login("aud.admin.neg@teste.local");
        String tokenUser = login("aud.user.neg@teste.local");
        long antes = maxAuditId();

        assertThat(postUsuario(tokenAdmin, Map.of("email", "escala@teste.local", "password", "Senha-Valida-123456", "role", "SUPER_ADMIN"))
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(postUsuario(tokenAdmin, Map.of("email", "isto-nao-e-email", "role", "USER")).getResponse().getStatus()).isEqualTo(400);
        assertThat(postUsuario(tokenAdmin, Map.of("email", "aud.existente@teste.local", "password", "Senha-Valida-123456"))
                .getResponse().getStatus()).isEqualTo(409);
        assertThat(postUsuario(tokenUser, Map.of("email", "x@teste.local", "password", "Senha-Valida-123456")).getResponse().getStatus()).isEqualTo(403);
        assertThat(put(existente.getId(), tokenUser, Map.of("ativo", false)).getResponse().getStatus()).isEqualTo(403);

        assertThat(depois(antes)).isEmpty();
    }

    @Test
    void eventosDeUmTenantNuncaReferenciamOutro_eTenantContextNaoEAfetado() throws Exception {
        Tenant a = novoTenant("ATIVO");
        Tenant b = novoTenant("ATIVO");
        criarUsuario("aud.iso.admin.a@teste.local", Role.TENANT_ADMIN, a.getId());
        criarUsuario("aud.iso.admin.b@teste.local", Role.TENANT_ADMIN, b.getId());
        String tokenA = login("aud.iso.admin.a@teste.local");
        String tokenB = login("aud.iso.admin.b@teste.local");
        long idA = corpoJson(postUsuario(tokenA, Map.of("email", "aud.iso.user.a@teste.local", "password", "Senha-Valida-123456"))).get("id").asLong();
        long idB = corpoJson(postUsuario(tokenB, Map.of("email", "aud.iso.user.b@teste.local", "password", "Senha-Valida-123456"))).get("id").asLong();

        // A tenta alterar o usuário de B: 404 (isolamento intacto) e nada é auditado como sucesso em B.
        long antes = maxAuditId();
        assertThat(put(idB, tokenA, Map.of("ativo", false)).getResponse().getStatus()).isEqualTo(404);
        assertThat(depois(antes)).isEmpty();

        for (AuditEvent e : auditRepository.findByTargetTenantIdOrderByIdAsc(a.getId())) {
            if (e.getActorTenantId() != null) {
                assertThat(e.getActorTenantId()).isEqualTo(a.getId());
            }
        }
        List<AuditEvent> criacoes = auditRepository.findByTargetTenantIdOrderByIdAsc(b.getId()).stream()
                .filter(e -> e.getAction() == AuditAction.USER_CREATED).toList();
        assertThat(criacoes).extracting(AuditEvent::getTargetUserId).contains(idB).doesNotContain(idA);
        // O tenant do ator sempre coincide com o tenant alvo dos eventos de gestão de usuários de TENANT.
        // (Exceção deliberada da 4.4.1: o SUPER_ADMIN, que não tem tenant, cria o TENANT_ADMIN inicial de uma empresa.)
        for (AuditEvent e : auditRepository.findAllByOrderByIdAsc()) {
            if (e.getAction() == AuditAction.USER_CREATED && e.getActorRole() != Role.SUPER_ADMIN) {
                assertThat(e.getActorTenantId()).isEqualTo(e.getTargetTenantId());
            }
        }
    }

    // ------------------------------------------------------------------ leitura / metadata
    @Test
    void nenhumEndpointDeLeituraDeAuditoriaEstaExpostoNestaEtapa() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.leitura.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("aud.leitura.user@teste.local", Role.USER, t.getId());
        criarUsuario("aud.leitura.super@teste.local", Role.SUPER_ADMIN, null);
        for (String email : List.of("aud.leitura.admin@teste.local", "aud.leitura.user@teste.local", "aud.leitura.super@teste.local")) {
            String token = login(email);
            for (String path : List.of("/api/audit", "/api/audit-events", "/api/admin/audit", "/api/auditoria")) {
                assertThat(status(get(path), token)).as(email + " " + path).isIn(403, 404);
            }
        }
    }

    @Test
    void metadataDeTodosOsEventosUsaSomenteChavesEValoresPermitidos() throws Exception {
        // Gera eventos variados (com dados próprios) antes de varrer a trilha inteira.
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.meta.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        User alvoMeta = criarUsuario("aud.meta.alvo@teste.local", Role.USER, t.getId());
        String tokenMeta = login("aud.meta.admin@teste.local");
        put(alvoMeta.getId(), tokenMeta, Map.of("role", "TENANT_ADMIN"));
        put(alvoMeta.getId(), tokenMeta, Map.of("cargo", "Cargo Meta", "ativo", false));
        tentarLogin("desconhecido.meta@teste.local", "qualquer-senha-123");

        Set<String> chavesPermitidas = Set.of("roleFrom", "roleTo", "activeFrom", "activeTo", "permissions",
                "permissionsAdded", "permissionsRemoved", "fields", "reasons",
                // Control Center (4.4.1): só enums, booleano e uma contagem inteira
                "tenantStatusFrom", "tenantStatusTo", "subscriptionStatusFrom", "subscriptionStatusTo",
                "planChanged", "overrideEffect", "count",
                // Financeiro (4.4.2): só enums do sistema (estado da cobrança, meio de pagamento, motivo de suspensão)
                "invoiceStatusFrom", "invoiceStatusTo", "paymentMethod", "suspensionReason",
                // Modo Suporte (4.4.3): só enums (modo e operação da allowlist)
                "supportMode", "supportOperation");
        int verificados = 0;
        for (AuditEvent e : auditRepository.findAllByOrderByIdAsc()) {
            if (e.getMetadata() == null) {
                continue;
            }
            assertThat(e.getMetadata().length()).isLessThanOrEqualTo(AuditMetadata.MAX_LENGTH);
            JsonNode m = json.readTree(e.getMetadata());
            Set<String> chaves = new HashSet<>();
            m.fieldNames().forEachRemaining(chaves::add);
            assertThat(chavesPermitidas).as("chaves de " + e.getAction()).containsAll(chaves);
            m.forEach(v -> {
                if (v.isArray()) {
                    v.forEach(x -> assertThat(x.asText()).matches("^[A-Z_]+$"));
                } else if (v.isTextual()) {
                    assertThat(v.asText()).matches("^[A-Z_]+$");
                } else {
                    assertThat(v.isBoolean() || v.isNull() || v.isInt()).as("valor de metadata: " + v).isTrue();
                }
            });
            verificados++;
        }
        assertThat(verificados).isGreaterThan(5);
    }

    @Test
    void userAgentEhLimitadoESaneadoNaPersistencia() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.ua@teste.local", Role.USER, t.getId());
        long antes = maxAuditId();
        // (CR/LF em header já é barrado pelo firewall do Spring Security; o saneamento de controles é coberto no teste unitário.)
        String uaMalicioso = "Mozilla/5.0 FORJADO 2026 ERROR " + "x".repeat(5000);
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "aud.ua@teste.local", "password", SENHA)))
                .header("User-Agent", uaMalicioso)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        AuditEvent e = unico(depois(antes), AuditAction.LOGIN_SUCCESS);
        assertThat(e.getUserAgent()).hasSizeLessThanOrEqualTo(200).doesNotContain("\r").doesNotContain("\n");
        assertThat(e.getUserAgent()).startsWith("Mozilla/5.0");
    }

    @Test
    void ipNaoConfiaEmXForwardedForPorPadrao() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("aud.xff@teste.local", Role.USER, t.getId());
        long antes = maxAuditId();
        MockHttpServletRequestBuilder req = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "aud.xff@teste.local", "password", SENHA)))
                .header("X-Forwarded-For", "6.6.6.6, 7.7.7.7").with(ip("203.0.113.44"));
        assertThat(mvc.perform(req).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(unico(depois(antes), AuditAction.LOGIN_SUCCESS).getRequestIp()).isEqualTo("203.0.113.44");
    }
}
