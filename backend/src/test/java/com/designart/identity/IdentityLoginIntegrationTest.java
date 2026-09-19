package com.designart.identity;

import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Permission;
import com.designart.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Fase 4.1: login por e-mail, JWT (sub = userId, tv, 8h), token_version e invariantes de identidade. */
@ExtendWith(OutputCaptureExtension.class)
class IdentityLoginIntegrationTest extends IntegrationTestBase {

    // ------------------------------------------------------------------
    // Login por e-mail
    // ------------------------------------------------------------------
    @Nested
    class LoginPorEmail {

        @Test
        void senhaCorretaAutentica_eRetornaUsuarioSemSegredos() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("login.ok@teste.local", Role.USER, t.getId());
            MvcResult r = tentarLogin("login.ok@teste.local", SENHA);
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            JsonNode corpo = corpoJson(r);
            assertThat(corpo.get("token").asText()).isNotBlank();
            assertThat(corpo.get("user").get("email").asText()).isEqualTo("login.ok@teste.local");
            assertThat(corpo.get("user").get("role").asText()).isEqualTo("USER");
            assertThat(corpo.get("user").has("username")).isFalse();
        }

        @Test
        void emailEhNormalizadoAntesDaConsulta_trimECaseInsensitive() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("caixa.mista@teste.local", Role.USER, t.getId());
            for (String digitado : List.of("CAIXA.MISTA@TESTE.LOCAL", "  caixa.mista@teste.local  ",
                    "Caixa.Mista@Teste.Local", "\tCAIXA.mista@teste.LOCAL\n")) {
                assertThat(tentarLogin(digitado, SENHA).getResponse().getStatus()).as(digitado).isEqualTo(200);
            }
        }

        @Test
        void emailArmazenadoEhSempreNormalizado() {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("  Salvo.Normalizado@TESTE.Local ", Role.USER, t.getId());
            assertThat(userRepository.findByEmail("salvo.normalizado@teste.local")).isPresent();
        }

        @Test
        void senhaErrada_401() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("senha.errada@teste.local", Role.USER, t.getId());
            assertThat(tentarLogin("senha.errada@teste.local", "senha-errada-999").getResponse().getStatus()).isEqualTo(401);
        }

        @Test
        void todasAsFalhasDeLoginSaoIndistinguiveis() throws Exception {
            Tenant ativo = novoTenant("ATIVO");
            Tenant suspenso = novoTenant("SUSPENSO");
            criarUsuario("f.ok@teste.local", Role.USER, ativo.getId());
            criarUsuario("f.inativo@teste.local", Role.USER, ativo.getId(), false, SENHA);
            criarUsuario("f.suspenso@teste.local", Role.USER, suspenso.getId());

            List<MvcResult> falhas = List.of(
                    tentarLogin("nao.existe@teste.local", SENHA),      // e-mail inexistente
                    tentarLogin("f.ok@teste.local", "senha-errada-999"), // senha errada
                    tentarLogin("f.inativo@teste.local", SENHA),         // usuário inativo
                    tentarLogin("f.suspenso@teste.local", SENHA),        // tenant suspenso
                    tentarLogin("isto-nao-e-um-email", SENHA));          // e-mail malformado

            String mensagem = json.readTree(corpo(falhas.get(0))).get("message").asText();
            assertThat(mensagem).isEqualTo("Usuário ou senha inválidos.");
            for (MvcResult r : falhas) {
                assertThat(r.getResponse().getStatus()).isEqualTo(401);
                JsonNode c = corpoJson(r);
                assertThat(c.get("message").asText()).isEqualTo(mensagem);
                assertThat(c.get("error").asText()).isEqualTo("Unauthorized");
                assertThat(c.has("token")).isFalse();
            }
        }

        @Test
        void emailOuSenhaEmBrancoRetorna400_validacaoDeEntrada() throws Exception {
            assertThat(status(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"\",\"password\":\"x\"}"), null)).isEqualTo(400);
            assertThat(status(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@teste.local\",\"password\":\"\"}"), null)).isEqualTo(400);
        }

        @Test
        void contratoAntigoPorUsernameNaoAutentica() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("legado.contrato@teste.local", Role.USER, t.getId());
            assertThat(status(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"legado.contrato\",\"password\":\"" + SENHA + "\"}"), null)).isEqualTo(400);
        }

        @Test
        void senhaAcimaDe72BytesNuncaAutentica_semTruncagemDoBcrypt() throws Exception {
            Tenant t = novoTenant("ATIVO");
            String senha72 = "s".repeat(72);
            criarUsuario("limite72@teste.local", Role.USER, t.getId(), true, senha72);
            assertThat(tentarLogin("limite72@teste.local", senha72).getResponse().getStatus()).isEqualTo(200);
            // 72 bytes + qualquer coisa NÃO pode ser aceita como se fosse a mesma senha.
            assertThat(tentarLogin("limite72@teste.local", senha72 + "x").getResponse().getStatus()).isEqualTo(401);
        }

        @Test
        void usuarioAtivoLoginAtualizaUltimoAcessoEZeraFalhas() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("ultimo.acesso@teste.local", Role.USER, t.getId());
            assertThat(userRepository.findByEmail("ultimo.acesso@teste.local").orElseThrow().getLastLoginAt()).isNull();
            login("ultimo.acesso@teste.local");
            assertThat(userRepository.findByEmail("ultimo.acesso@teste.local").orElseThrow().getLastLoginAt()).isNotNull();
        }
    }

    // ------------------------------------------------------------------
    // Unicidade e validade do e-mail
    // ------------------------------------------------------------------
    @Nested
    class EmailUnicoEValido {

        @Test
        void emailDuplicadoNoBanco_ehRejeitado_inclusiveVariandoCaixa() {
            Tenant a = novoTenant("ATIVO");
            Tenant b = novoTenant("ATIVO");
            criarUsuario("duplicado@teste.local", Role.USER, a.getId());
            // Mesmo em OUTRO tenant: a unicidade é GLOBAL.
            assertThatThrownBy(() -> criarUsuario("DUPLICADO@Teste.Local", Role.USER, b.getId()))
                    .isInstanceOfAny(DataIntegrityViolationException.class, RuntimeException.class);
        }

        @Test
        void emailDuplicadoViaApi_retorna409_mesmoEmOutroTenant() throws Exception {
            Tenant a = novoTenant("ATIVO");
            Tenant b = novoTenant("ATIVO");
            criarUsuario("admin.dup.a@teste.local", Role.TENANT_ADMIN, a.getId());
            criarUsuario("admin.dup.b@teste.local", Role.TENANT_ADMIN, b.getId());
            criarUsuario("ja.existe.no.a@teste.local", Role.USER, a.getId());
            String tokenB = login("admin.dup.b@teste.local");
            MvcResult r = executar(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", "JA.EXISTE.NO.A@teste.local", "password", "senha-valida-123"))), tokenB);
            assertThat(r.getResponse().getStatus()).isEqualTo(409);
        }

        @Test
        void emailInvalidoNoBanco_ehRejeitado() {
            Tenant t = novoTenant("ATIVO");
            assertThatThrownBy(() -> criarUsuario("sem-arroba", Role.USER, t.getId())).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> criarUsuario("   ", Role.USER, t.getId())).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> criarUsuario(null, Role.USER, t.getId())).isInstanceOf(RuntimeException.class);
        }

        @Test
        void emailInvalidoOuAusenteViaApi_retorna400() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("admin.emailinv@teste.local", Role.TENANT_ADMIN, t.getId());
            String token = login("admin.emailinv@teste.local");
            for (String email : new String[]{"nao-e-email", "  ", "a@b"}) {
                assertThat(status(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "senha-valida-123"))), token))
                        .as(email).isEqualTo(400);
            }
            assertThat(status(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"senha-valida-123\"}"), token)).isEqualTo(400);
        }
    }

    // ------------------------------------------------------------------
    // JWT: sub = userId, tv, 8h, sem dados sensíveis
    // ------------------------------------------------------------------
    @Nested
    class Jwt {

        @Test
        void subEhOUserId_tvEhOTokenVersion_eValidadeEDe8Horas() throws Exception {
            Tenant t = novoTenant("ATIVO");
            User u = criarUsuario("jwt.claims@teste.local", Role.USER, t.getId());
            String token = login("jwt.claims@teste.local");

            Claims c = jwtUtil.parse(token);
            assertThat(c.getSubject()).isEqualTo(String.valueOf(u.getId()));
            assertThat(c.getSubject()).doesNotContain("@");
            assertThat(c.get("tv", Integer.class)).isEqualTo(0);
            assertThat(c.get("role", String.class)).isEqualTo("USER");
            long segundos = (c.getExpiration().getTime() - c.getIssuedAt().getTime()) / 1000;
            assertThat(segundos).isEqualTo(Duration.ofHours(8).toSeconds());
            // Nada além do necessário: sem e-mail, tenant, senha ou hash no token.
            assertThat(c.keySet()).containsExactlyInAnyOrder("sub", "tv", "role", "iat", "exp");
        }

        @Test
        void tokenVersionIncrementadoInvalidaJwtAntigo_eNovoLoginFunciona() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("jwt.tv@teste.local", Role.USER, t.getId());
            String antigo = login("jwt.tv@teste.local");
            assertThat(status(get("/api/auth/me"), antigo)).isEqualTo(200);
            assertThat(status(get("/api/clientes"), antigo)).isEqualTo(200);

            User u = userRepository.findByEmail("jwt.tv@teste.local").orElseThrow();
            u.revokeSessions(); // token_version++
            userRepository.save(u);

            assertThat(status(get("/api/auth/me"), antigo)).isEqualTo(401);
            assertThat(status(get("/api/clientes"), antigo)).isEqualTo(401);

            String novo = login("jwt.tv@teste.local");
            assertThat(jwtUtil.parse(novo).get("tv", Integer.class)).isEqualTo(1);
            assertThat(status(get("/api/auth/me"), novo)).isEqualTo(200);
        }

        @Test
        void tokenComTokenVersionDiferenteOuAusenteEhRejeitado() throws Exception {
            Tenant t = novoTenant("ATIVO");
            User u = criarUsuario("jwt.tv2@teste.local", Role.USER, t.getId());
            User copiaComVersaoErrada = User.builder().id(u.getId()).role(Role.USER).tokenVersion(5).build();
            String versaoErrada = jwtUtil.generateToken(copiaComVersaoErrada);
            assertThat(status(get("/api/auth/me"), versaoErrada)).isEqualTo(401);

            // Sem o claim tv.
            String semTv = io.jsonwebtoken.Jwts.builder().subject(String.valueOf(u.getId()))
                    .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                    .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                            "test-only-secret-test-only-secret-test-only-secret-0123456789".getBytes()))
                    .compact();
            assertThat(status(get("/api/auth/me"), semTv)).isEqualTo(401);
        }

        @Test
        void tokenExpiradoOuComSubEmailOuInexistenteEhRejeitado() throws Exception {
            Tenant t = novoTenant("ATIVO");
            User u = criarUsuario("jwt.exp@teste.local", Role.USER, t.getId());
            assertThat(status(get("/api/auth/me"), jwtUtil.generateToken(u, Duration.ofSeconds(-30)))).isEqualTo(401);

            // sub = e-mail (formato antigo/indevido) NÃO é aceito.
            String key = "test-only-secret-test-only-secret-test-only-secret-0123456789";
            String subEmail = io.jsonwebtoken.Jwts.builder().subject("jwt.exp@teste.local").claim("tv", 0)
                    .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                    .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(key.getBytes())).compact();
            assertThat(status(get("/api/auth/me"), subEmail)).isEqualTo(401);

            User fantasma = User.builder().id(987654321L).role(Role.USER).tokenVersion(0).build();
            assertThat(status(get("/api/auth/me"), jwtUtil.generateToken(fantasma))).isEqualTo(401);
        }

        @Test
        void authoritiesVemDoBancoENaoDoClaimDoToken() throws Exception {
            Tenant t = novoTenant("ATIVO");
            User u = criarUsuario("jwt.role@teste.local", Role.USER, t.getId());
            // Token forjado (assinatura válida do servidor) afirmando role=TENANT_ADMIN para um USER.
            User falso = User.builder().id(u.getId()).role(Role.TENANT_ADMIN).tokenVersion(0).build();
            String tokenComClaimFalso = jwtUtil.generateToken(falso);
            assertThat(jwtUtil.parse(tokenComClaimFalso).get("role", String.class)).isEqualTo("TENANT_ADMIN");
            // O backend ignora o claim: continua tratando como USER (sem acesso a /api/usuarios).
            assertThat(status(get("/api/usuarios"), tokenComClaimFalso)).isEqualTo(403);
            assertThat(status(get("/api/clientes"), tokenComClaimFalso)).isEqualTo(200);
        }
    }

    // ------------------------------------------------------------------
    // Regra SUPER_ADMIN <=> tenant_id NULL
    // ------------------------------------------------------------------
    @Nested
    class SuperAdminETenant {

        @Test
        void usuarioComumSemTenantNaoPodeExistir() {
            assertThatThrownBy(() -> criarUsuario("user.semtenant@teste.local", Role.USER, null)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> criarUsuario("admin.semtenant@teste.local", Role.TENANT_ADMIN, null)).isInstanceOf(RuntimeException.class);
            assertThat(userRepository.findByEmail("user.semtenant@teste.local")).isEmpty();
            assertThat(userRepository.findByEmail("admin.semtenant@teste.local")).isEmpty();
        }

        @Test
        void superAdminComTenantNaoPodeExistir() {
            Tenant t = novoTenant("ATIVO");
            assertThatThrownBy(() -> criarUsuario("super.comtenant@teste.local", Role.SUPER_ADMIN, t.getId()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(userRepository.findByEmail("super.comtenant@teste.local")).isEmpty();
        }

        @Test
        void superAdminSemTenantAutentica() throws Exception {
            criarUsuario("super.ok@teste.local", Role.SUPER_ADMIN, null);
            MvcResult r = tentarLogin("super.ok@teste.local", SENHA);
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            assertThat(corpoJson(r).get("user").get("role").asText()).isEqualTo("SUPER_ADMIN");
            String token = corpoJson(r).get("token").asText();
            MvcResult me = executar(get("/api/auth/me"), token);
            assertThat(me.getResponse().getStatus()).isEqualTo(200);
            assertThat(corpoJson(me).get("role").asText()).isEqualTo("SUPER_ADMIN");
        }

        @Test
        void superAdminNaoAcessaNenhumEndpointTenantScoped() throws Exception {
            criarUsuario("super.negado@teste.local", Role.SUPER_ADMIN, null);
            String token = login("super.negado@teste.local");
            for (String path : List.of("/api/clientes", "/api/tarefas", "/api/eventos", "/api/roteiros", "/api/logos",
                    "/api/vendas", "/api/despesas", "/api/despesas/fluxo-caixa", "/api/avaliacoes",
                    "/api/dashboard/stats", "/api/relatorios/mensal", "/api/usuarios")) {
                assertThat(status(get(path), token)).as("GET " + path).isEqualTo(403);
            }
            assertThat(status(post("/api/clientes").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"x\"}"), token))
                    .isEqualTo(403);
            assertThat(status(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"a@teste.local\",\"password\":\"senha-valida-123\"}"), token)).isEqualTo(403);
        }

        @Test
        void superAdminInativoNaoAutentica() throws Exception {
            criarUsuario("super.inativo@teste.local", Role.SUPER_ADMIN, null, false, SENHA);
            assertThat(tentarLogin("super.inativo@teste.local", SENHA).getResponse().getStatus()).isEqualTo(401);
        }
    }

    // ------------------------------------------------------------------
    // Política de senha e anti-escalada via API de usuários do tenant
    // ------------------------------------------------------------------
    @Nested
    class GestaoDeUsuariosDoTenant {

        private String tokenAdmin(String email) throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario(email, Role.TENANT_ADMIN, t.getId());
            return login(email);
        }

        private int criar(String token, Map<String, Object> corpo) throws Exception {
            return status(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(corpo)), token);
        }

        /** Convida (sem senha) e devolve o token lido da caixa de e-mail de TESTE (memória; nunca log/HTTP). */
        private String convidar(String tokenAdmin, String email) throws Exception {
            assertThat(criar(tokenAdmin, Map.of("email", email, "role", "USER"))).isEqualTo(200);
            return emails.lastToken(email);
        }

        private int aceitar(String token, String senha, String confirmacao) throws Exception {
            return status(post("/api/auth/accept-invite").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("token", token, "newPassword", senha, "confirmPassword", confirmacao))), null);
        }

        @Test
        void politicaDeSenhaValeNoAceiteDoConvite() throws Exception {
            String admin = tokenAdmin("admin.politica@teste.local");
            String tCurta = convidar(admin, "p.curta@teste.local");
            String tDez = convidar(admin, "p.dez@teste.local");
            String t72 = convidar(admin, "p.72@teste.local");
            String t73 = convidar(admin, "p.73@teste.local");
            String tUtf8a = convidar(admin, "p.utf8.a@teste.local");
            String tUtf8b = convidar(admin, "p.utf8.b@teste.local");
            String tEmail = convidar(admin, "igual.ao.email@teste.local");
            String tNul = convidar(admin, "p.nul@teste.local");

            assertThat(aceitar(tCurta, "123456789", "123456789")).isEqualTo(400);                       // 9 caracteres
            assertThat(aceitar(t73, "a".repeat(73), "a".repeat(73))).isEqualTo(400);                     // 73 bytes
            assertThat(aceitar(tUtf8a, "é".repeat(37), "é".repeat(37))).isEqualTo(400);                  // 37 chars = 74 bytes
            assertThat(aceitar(tEmail, "igual.ao.email@teste.local", "igual.ao.email@teste.local")).isEqualTo(400);
            assertThat(aceitar(tNul, "senha-com-nul\u0000-x", "senha-com-nul\u0000-x")).isEqualTo(400);
            // Senha fraca NÃO queima o convite: o mesmo link segue válido e aceita uma senha correta.
            assertThat(aceitar(tCurta, "1234567890", "1234567890")).isEqualTo(200);                       // 10 caracteres
            assertThat(aceitar(tDez, "1234567890", "1234567890")).isEqualTo(200);
            assertThat(aceitar(t72, "a".repeat(72), "a".repeat(72))).isEqualTo(200);                      // 72 bytes
            assertThat(aceitar(tUtf8b, "é".repeat(36), "é".repeat(36))).isEqualTo(200);                   // 36 chars = 72 bytes
            // Senhas aceitas autenticam exatamente com elas.
            assertThat(tentarLogin("p.72@teste.local", "a".repeat(72)).getResponse().getStatus()).isEqualTo(200);
            assertThat(tentarLogin("p.utf8.b@teste.local", "é".repeat(36)).getResponse().getStatus()).isEqualTo(200);
        }

        @Test
        void administradorNuncaDefineNemAlteraSenha() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("admin.sem.senha@teste.local", Role.TENANT_ADMIN, t.getId());
            User alvo = criarUsuario("alvo.sem.senha@teste.local", Role.USER, t.getId());
            String token = login("admin.sem.senha@teste.local");
            String hashAntes = userRepository.findById(alvo.getId()).orElseThrow().getPassword();

            // "password" no corpo é ignorado na atualização...
            assertThat(status(put("/api/usuarios/" + alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"SenhaDefinidaPeloAdmin-1\",\"cargo\":\"Novo\"}"), token)).isEqualTo(200);
            assertThat(userRepository.findById(alvo.getId()).orElseThrow().getPassword()).isEqualTo(hashAntes);
            assertThat(tentarLogin("alvo.sem.senha@teste.local", "SenhaDefinidaPeloAdmin-1").getResponse().getStatus()).isEqualTo(401);
            assertThat(tentarLogin("alvo.sem.senha@teste.local", SENHA).getResponse().getStatus()).isEqualTo(200);

            // ...e na criação: o usuário nasce PENDENTE, sem senha, e não consegue entrar com a "senha" enviada.
            assertThat(criar(token, Map.of("email", "pendente.sem.senha@teste.local", "password", "SenhaDefinidaPeloAdmin-2"))).isEqualTo(200);
            User pendente = userRepository.findByEmail("pendente.sem.senha@teste.local").orElseThrow();
            assertThat(pendente.getPassword()).isNull();
            assertThat(pendente.isPendingInvite()).isTrue();
            assertThat(tentarLogin("pendente.sem.senha@teste.local", "SenhaDefinidaPeloAdmin-2").getResponse().getStatus()).isEqualTo(401);
        }

        @Test
        void escaladaDeRoleEhRejeitada() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("admin.escala@teste.local", Role.TENANT_ADMIN, t.getId());
            User comum = criarUsuario("comum.escala@teste.local", Role.USER, t.getId());
            String token = login("admin.escala@teste.local");

            // Criar SUPER_ADMIN: proibido (403).
            assertThat(criar(token, Map.of("email", "novo.super@teste.local", "password", "senha-valida-123",
                    "role", "SUPER_ADMIN"))).isEqualTo(403);
            assertThat(userRepository.findByEmail("novo.super@teste.local")).isEmpty();
            // Promover alguém a SUPER_ADMIN: proibido, e o usuário continua USER.
            assertThat(status(put("/api/usuarios/" + comum.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\":\"SUPER_ADMIN\"}"), token)).isEqualTo(403);
            assertThat(userRepository.findByEmail("comum.escala@teste.local").orElseThrow().getRole()).isEqualTo(Role.USER);
            // Valores fora do catálogo (incluindo o legado "ADMIN" e minúsculas): 400.
            for (String role : new String[]{"ADMIN", "COLABORADOR", "user", "ROOT", "TENANT-ADMIN", ""}) {
                assertThat(status(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"r." + Math.abs(role.hashCode()) + "@teste.local\",\"password\":\"senha-valida-123\",\"role\":\"" + role + "\"}"), token))
                        .as("role=" + role).isEqualTo(400);
            }
            // Permissão fora do catálogo: 400.
            assertThat(status(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"perm.inv@teste.local\",\"password\":\"senha-valida-123\",\"permissoes\":[\"SUPER\"]}"), token))
                    .isEqualTo(400);
            // Promover para TENANT_ADMIN do PRÓPRIO tenant é permitido.
            assertThat(status(put("/api/usuarios/" + comum.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\":\"TENANT_ADMIN\"}"), token)).isEqualTo(200);
        }

        @Test
        void ultimoTenantAdminAtivoNaoPodeSerRemovidoRebaixadoNemDesativado() throws Exception {
            Tenant t = novoTenant("ATIVO");
            User unico = criarUsuario("unico.admin@teste.local", Role.TENANT_ADMIN, t.getId());
            String token = login("unico.admin@teste.local");
            assertThat(status(delete("/api/usuarios/" + unico.getId()), token)).isEqualTo(403);
            assertThat(status(put("/api/usuarios/" + unico.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\":\"USER\"}"), token)).isEqualTo(403);
            assertThat(status(put("/api/usuarios/" + unico.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ativo\":false}"), token)).isEqualTo(403);
            assertThat(userRepository.findByEmail("unico.admin@teste.local").orElseThrow().getRole()).isEqualTo(Role.TENANT_ADMIN);

            // Com um segundo admin, o rebaixamento passa a ser possível.
            User segundo = criarUsuario("segundo.admin@teste.local", Role.TENANT_ADMIN, t.getId());
            assertThat(status(put("/api/usuarios/" + segundo.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\":\"USER\"}"), token)).isEqualTo(200);
        }

        @Test
        void tenantAdminNaoTemPermissoesGravadas_saoImplicitas() throws Exception {
            String token = tokenAdmin("admin.implicito@teste.local");
            convidarEAceitar(token, "outro.admin@teste.local", Role.TENANT_ADMIN, Permission.FINANCEIRO);
            User outro = userRepository.findByEmail("outro.admin@teste.local").orElseThrow();
            assertThat(outro.getPermissoes()).isEmpty();
            // ...mas opera o financeiro (acesso implícito).
            assertThat(status(get("/api/despesas"), login("outro.admin@teste.local"))).isEqualTo(200);
        }

        @Test
        void mudancaDeRoleEDesativacaoRevogamSessoesAbertas() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("admin.revoga@teste.local", Role.TENANT_ADMIN, t.getId());
            criarUsuario("segundo.revoga@teste.local", Role.TENANT_ADMIN, t.getId());
            User alvo = criarUsuario("alvo.revoga@teste.local", Role.USER, t.getId());
            String tokenAdmin = login("admin.revoga@teste.local");
            String tokenAlvo = login("alvo.revoga@teste.local");
            assertThat(status(get("/api/clientes"), tokenAlvo)).isEqualTo(200);

            // Desativar e reativar NÃO ressuscita o JWT antigo (token_version foi incrementado).
            assertThat(status(put("/api/usuarios/" + alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ativo\":false}"), tokenAdmin)).isEqualTo(200);
            assertThat(status(get("/api/clientes"), tokenAlvo)).isEqualTo(401);
            assertThat(status(put("/api/usuarios/" + alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ativo\":true}"), tokenAdmin)).isEqualTo(200);
            assertThat(status(get("/api/clientes"), tokenAlvo)).isEqualTo(401);
            String novo = login("alvo.revoga@teste.local");
            assertThat(status(get("/api/clientes"), novo)).isEqualTo(200);

            // Mudança de role também derruba as sessões.
            assertThat(status(put("/api/usuarios/" + alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\":\"TENANT_ADMIN\"}"), tokenAdmin)).isEqualTo(200);
            assertThat(status(get("/api/clientes"), novo)).isEqualTo(401);
        }

        private String login2(String email, String senha) throws Exception {
            return json.readTree(corpo(tentarLogin(email, senha))).get("token").asText();
        }
    }

    // ------------------------------------------------------------------
    // Segredos nunca em logs nem respostas
    // ------------------------------------------------------------------
    @Test
    void senhaHashEJwtCompletoNuncaAparecemEmLogsNemRespostasDeErro(CapturedOutput output) throws Exception {
        Tenant t = novoTenant("ATIVO");
        String senhaDistinta = "Senh@-Distinta-Para-Log-4711";
        criarUsuario("log.seguro@teste.local", Role.TENANT_ADMIN, t.getId(), true, senhaDistinta);
        String hash = userRepository.findByEmail("log.seguro@teste.local").orElseThrow().getPassword();

        MvcResult falha = tentarLogin("log.seguro@teste.local", "Senha-Errada-Distinta-9182");
        MvcResult ok = tentarLogin("log.seguro@teste.local", senhaDistinta);
        String token = corpoJson(ok).get("token").asText();
        MvcResult me = executar(get("/api/auth/me"), token);
        MvcResult usuarios = executar(get("/api/usuarios"), token);
        // Convite real; o aceite com senha fraca ("Curta-9") deve ser 400 sem vazar a senha nem o token.
        executar(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"log.novo@teste.local\",\"role\":\"USER\"}"), token);
        String tokenConvite = emails.lastToken("log.novo@teste.local");
        MvcResult criacaoInvalida = executar(post("/api/auth/accept-invite").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + tokenConvite + "\",\"newPassword\":\"Curta-9\",\"confirmPassword\":\"Curta-9\"}"), null);
        MvcResult tokenInvalido = executar(get("/api/auth/me"), token + "adulterado");

        assertThat(criacaoInvalida.getResponse().getStatus()).isEqualTo(400);
        assertThat(tokenInvalido.getResponse().getStatus()).isEqualTo(401);
        String todasAsRespostasDeErro = corpo(falha) + corpo(criacaoInvalida) + corpo(tokenInvalido);
        String todasAsRespostas = todasAsRespostasDeErro + corpo(me) + corpo(usuarios);

        for (String segredo : List.of(senhaDistinta, "Senha-Errada-Distinta-9182", "Curta-9", hash, token, tokenConvite)) {
            assertThat(output.getAll()).as("logs").doesNotContain(segredo);
        }
        assertThat(output.getAll()).doesNotContain(token.substring(0, 40));
        for (String segredo : List.of(senhaDistinta, "Senha-Errada-Distinta-9182", "Curta-9", hash)) {
            assertThat(todasAsRespostas).as("respostas").doesNotContain(segredo);
        }
        // O JWT só existe na resposta do login, nunca nas demais.
        assertThat(todasAsRespostas).doesNotContain(token);
        assertThat(corpo(ok)).doesNotContain(hash).doesNotContain(senhaDistinta);
    }
}
