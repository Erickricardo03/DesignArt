package com.designart.tenant;

import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.repository.TenantRepository;
import com.designart.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Fase 3 (ajustes finais): /api/auth/me, usuário ativo/inativo, status do
 * tenant (enforcement) e ausência de dados fictícios nas métricas.
 * Todos os tenants/usuários existem só no H2 de teste.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessControlIntegrationTest {

    static final String SENHA = "senha-de-teste-123";
    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    // GET /api/auth/me
    // ------------------------------------------------------------------
    @Nested
    class Me {

        @Test
        void semTokenRetorna401ENuncaFabricaUsuario() throws Exception {
            MvcResult r = mvc.perform(get("/api/auth/me")).andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            String corpo = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(corpo).doesNotContain("\"username\"").doesNotContain("ADMIN").doesNotContain("Administrador");
        }

        @Test
        void tokenInvalidoRetorna401() throws Exception {
            assertThat(status(get("/api/auth/me"), "lixo.lixo.lixo")).isEqualTo(401);
        }

        @Test
        void autenticadoRetornaExclusivamenteOUsuarioReal() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("me.real", com.designart.security.Role.USER, t.getId(), true);
            criarUsuario("outro.user", com.designart.security.Role.TENANT_ADMIN, t.getId(), true);
            String token = login("me.real");

            MvcResult r = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            JsonNode u = json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(u.get("email").asText()).isEqualTo("me.real@teste.local");
            assertThat(u.get("role").asText()).isEqualTo("USER");
            assertThat(u.get("id").asLong()).isEqualTo(userRepository.findByEmail("me.real@teste.local").orElseThrow().getId());
        }
    }

    // ------------------------------------------------------------------
    // Usuário ativo / inativo
    // ------------------------------------------------------------------
    @Nested
    class UsuarioAtivo {

        @Test
        void usuarioAtivoConsegueLoginEAcessa() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("ativo.ok", com.designart.security.Role.TENANT_ADMIN, t.getId(), true);
            assertThat(status(get("/api/clientes"), login("ativo.ok"))).isEqualTo(200);
        }

        @Test
        void usuarioInativoNaoConsegueLogin() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("inativo.login", com.designart.security.Role.TENANT_ADMIN, t.getId(), false);
            MvcResult r = tentarLogin("inativo.login");
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            // Mesma resposta genérica de "senha errada": não revela que a conta existe/está inativa.
            assertThat(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Usuário ou senha inválidos.")
                    .doesNotContainIgnoringCase("inativ").doesNotContainIgnoringCase("desativ");
        }

        @Test
        void jwtEmitidoAntesDaDesativacaoDeixaDeFuncionar() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("desativado.depois", com.designart.security.Role.TENANT_ADMIN, t.getId(), true);
            String token = login("desativado.depois");
            assertThat(status(get("/api/clientes"), token)).isEqualTo(200);
            assertThat(status(get("/api/auth/me"), token)).isEqualTo(200);

            User u = userRepository.findByEmail("desativado.depois@teste.local").orElseThrow();
            u.setAtivo(false);
            userRepository.save(u);

            assertThat(status(get("/api/clientes"), token)).isEqualTo(401);
            assertThat(status(get("/api/auth/me"), token)).isEqualTo(401);
            assertThat(status(get("/api/usuarios"), token)).isEqualTo(401);
        }
    }

    // ------------------------------------------------------------------
    // Status do tenant
    // ------------------------------------------------------------------
    @Nested
    class StatusDoTenant {

        @Test
        void tenantAtivoPermiteAcesso() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("tenant.ativo", com.designart.security.Role.TENANT_ADMIN, t.getId(), true);
            assertThat(status(get("/api/clientes"), login("tenant.ativo"))).isEqualTo(200);
        }

        @ParameterizedTest(name = "tenant {0}: novo login negado")
        @ValueSource(strings = {"SUSPENSO", "INATIVO"})
        void tenantNaoAtivoNaoPermiteLogin(String statusTenant) throws Exception {
            Tenant t = novoTenant(statusTenant);
            criarUsuario("login." + statusTenant.toLowerCase(), com.designart.security.Role.TENANT_ADMIN, t.getId(), true);
            MvcResult r = tentarLogin("login." + statusTenant.toLowerCase());
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Usuário ou senha inválidos.")
                    .doesNotContainIgnoringCase("suspens").doesNotContainIgnoringCase("tenant");
        }

        @ParameterizedTest(name = "JWT antigo deixa de funcionar após tenant virar {0}")
        @ValueSource(strings = {"SUSPENSO", "INATIVO"})
        void jwtAntigoPerdeAcessoQuandoTenantEDesativado(String novoStatus) throws Exception {
            Tenant t = novoTenant("ATIVO");
            String username = "jwt.antigo." + novoStatus.toLowerCase();
            criarUsuario(username, com.designart.security.Role.TENANT_ADMIN, t.getId(), true);
            String token = login(username);
            assertThat(status(get("/api/clientes"), token)).isEqualTo(200);

            t.setStatus(novoStatus);
            tenantRepository.save(t);

            assertThat(status(get("/api/clientes"), token)).isEqualTo(401);
            assertThat(status(get("/api/dashboard/stats"), token)).isEqualTo(401);
            assertThat(status(get("/api/usuarios"), token)).isEqualTo(401);

            // O estado é lido a cada requisição: reativar o tenant restabelece o acesso.
            t.setStatus("ATIVO");
            tenantRepository.save(t);
            assertThat(status(get("/api/clientes"), token)).isEqualTo(200);
        }

        @Test
        void suspensaoAfetaSoOTenantSuspenso() throws Exception {
            Tenant suspenso = novoTenant("ATIVO");
            Tenant vizinho = novoTenant("ATIVO");
            criarUsuario("susp.a", com.designart.security.Role.TENANT_ADMIN, suspenso.getId(), true);
            criarUsuario("susp.b", com.designart.security.Role.TENANT_ADMIN, vizinho.getId(), true);
            String tokenA = login("susp.a");
            String tokenB = login("susp.b");

            suspenso.setStatus("SUSPENSO");
            tenantRepository.save(suspenso);

            assertThat(status(get("/api/clientes"), tokenA)).isEqualTo(401);
            assertThat(status(get("/api/clientes"), tokenB)).isEqualTo(200);
        }

        @Test
        void tenantInexistenteFalhaFechado() throws Exception {
            // Duas camadas de defesa: no PostgreSQL a FK users.tenant_id -> tenants(id) já impede
            // criar o usuário; sem FK (H2 de teste) a AccessPolicy nega o login. Em ambos, falha fechado.
            try {
                criarUsuario("tenant.fantasma", com.designart.security.Role.TENANT_ADMIN, 987654321L, true);
            } catch (org.springframework.dao.DataIntegrityViolationException fkDoBanco) {
                return;
            }
            assertThat(tentarLogin("tenant.fantasma").getResponse().getStatus()).isEqualTo(401);
        }
    }

    // ------------------------------------------------------------------
    // Produção não retorna números/pessoas fictícios
    // ------------------------------------------------------------------
    @Nested
    class SemDadosFicticios {

        private static final List<String> TERMOS_FICTICIOS = List.of(
                "OSMAR", "ANTONIO AURELIO", "HUMBERTO", "REBECA", "MAIARA", "SÁVILO", "WYTHCEL",
                "WALTER BERNARDO", "YAINARIS", "Lucas Matheus", "BARRA RUN");

        @Test
        void tenantSemDadosRecebeSomenteZerosEListasVazias() throws Exception {
            Tenant t = novoTenant("ATIVO");
            criarUsuario("vazio.tenant", com.designart.security.Role.TENANT_ADMIN, t.getId(), true);
            String token = login("vazio.tenant");

            String corpoStats = corpoTexto(get("/api/dashboard/stats"), token);
            String corpoCaixa = corpoTexto(get("/api/despesas/fluxo-caixa"), token);
            String corpoRel = corpoTexto(get("/api/relatorios/mensal"), token);
            for (String termo : TERMOS_FICTICIOS) {
                assertThat(corpoStats + corpoCaixa + corpoRel).doesNotContainIgnoringCase(termo);
            }

            JsonNode s = json.readTree(corpoStats);
            assertThat(s.get("visitasNaPagina").asLong()).isZero(); // nunca "324"
            assertThat(s.get("totalTarefas").asInt()).isZero();
            // Nomes de campo esperados pelo frontend (antes o Lombok gerava "areceber"/"afazer").
            assertThat(s.has("aReceber") && s.has("aFazer")).isTrue();
            assertThat(s.has("areceber") || s.has("afazer")).isFalse();
            assertThat(s.get("ganhosNoMes").decimalValue()).isEqualByComparingTo("0");
            assertThat(s.get("aReceber").decimalValue()).isEqualByComparingTo("0");
            assertThat(s.get("atrasados").decimalValue()).isEqualByComparingTo("0");
            assertThat(s.get("rankingColaboradores")).isEmpty();
            assertThat(s.get("ultimasVendas")).isEmpty();
            assertThat(s.get("avisos")).isEmpty();
            assertThat(s.get("producaoMensal")).hasSize(12);
            s.get("producaoMensal").forEach(m -> {
                assertThat(m.get("atendimentos").asInt()).isZero();
                assertThat(m.get("concluidos").asInt()).isZero();
            });

            JsonNode c = json.readTree(corpoCaixa);
            assertThat(c.get("totalEntradas").decimalValue()).isEqualByComparingTo("0");
            assertThat(c.get("totalSaidas").decimalValue()).isEqualByComparingTo("0");
            assertThat(c.get("lucroLiquido").decimalValue()).isEqualByComparingTo("0");
            assertThat(c.get("ultimasDespesas")).isEmpty();
            assertThat(c.get("comparativosMensais")).hasSize(12);
            c.get("comparativosMensais").forEach(m -> {
                assertThat(m.get("entradas").decimalValue()).isEqualByComparingTo("0");
                assertThat(m.get("saidas").decimalValue()).isEqualByComparingTo("0");
                assertThat(m.get("lucro").decimalValue()).isEqualByComparingTo("0");
            });

            assertThat(json.readTree(corpoRel)).isEmpty();
        }

        @Test
        void metricasSaoCalculadasApenasComDadosReaisDoTenant() throws Exception {
            Tenant t = novoTenant("ATIVO");
            Tenant outro = novoTenant("ATIVO");
            criarUsuario("real.tenant", com.designart.security.Role.TENANT_ADMIN, t.getId(), true);
            criarUsuario("real.outro", com.designart.security.Role.TENANT_ADMIN, outro.getId(), true);
            String token = login("real.tenant");
            String tokenOutro = login("real.outro");

            int ano = LocalDate.now().getYear();
            criar(token, "/api/tarefas", Map.of("titulo", "real", "loja", "l", "status", "CONCLUIDA",
                    "dataEntrega", ano + "-03-10", "responsaveis", List.of("Pessoa Real")));
            // Dados de OUTRO tenant não podem aparecer no ranking/produção deste.
            criar(tokenOutro, "/api/tarefas", Map.of("titulo", "alheia", "loja", "l", "status", "CONCLUIDA",
                    "dataEntrega", ano + "-03-11", "responsaveis", List.of("Pessoa Alheia")));
            criar(token, "/api/vendas", Map.of("valorTotal", 40, "status", "PAGO"));
            criar(token, "/api/despesas", Map.of("descricao", "d", "categoria", "c", "valor", 15,
                    "dataDespesa", ano + "-03-05"));

            JsonNode s = json.readTree(corpoTexto(get("/api/dashboard/stats"), token));
            assertThat(s.get("rankingColaboradores")).hasSize(1);
            assertThat(s.get("rankingColaboradores").get(0).get("nome").asText()).isEqualTo("Pessoa Real");
            assertThat(s.get("rankingColaboradores").get(0).get("totalTarefasConcluidas").asLong()).isEqualTo(1);
            JsonNode marco = s.get("producaoMensal").get(2);
            assertThat(marco.get("atendimentos").asInt()).isEqualTo(1);
            assertThat(marco.get("concluidos").asInt()).isEqualTo(1);
            assertThat(s.get("producaoMensal").get(0).get("atendimentos").asInt()).isZero();

            JsonNode c = json.readTree(corpoTexto(get("/api/despesas/fluxo-caixa"), token));
            assertThat(c.get("totalEntradas").decimalValue()).isEqualByComparingTo("40");
            assertThat(c.get("totalSaidas").decimalValue()).isEqualByComparingTo("15");
            assertThat(c.get("comparativosMensais").get(2).get("saidas").decimalValue()).isEqualByComparingTo("15");
            assertThat(c.get("comparativosMensais").get(0).get("entradas").decimalValue()).isEqualByComparingTo("0");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private Tenant novoTenant(String status) {
        int n = SEQ.incrementAndGet();
        return tenantRepository.save(Tenant.builder().name("Tenant acesso " + n)
                .slug("acesso-teste-" + n).status(status).build());
    }

    private void criarUsuario(String username, com.designart.security.Role role, Long tenantId, boolean ativo) {
        userRepository.save(User.builder().email(username + "@teste.local").password(passwordEncoder.encode(SENHA))
                .nomeCompleto(username).role(role).ativo(ativo).tenantId(tenantId).build());
    }

    private MvcResult tentarLogin(String username) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", username + "@teste.local", "password", SENHA)))).andReturn();
    }

    private String login(String username) throws Exception {
        MvcResult r = tentarLogin(username);
        assertThat(r.getResponse().getStatus()).as("login " + username).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("token").asText();
    }

    private void criar(String token, String path, Map<String, Object> corpo) throws Exception {
        MvcResult r = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token).content(json.writeValueAsString(corpo))).andReturn();
        assertThat(r.getResponse().getStatus()).as("POST " + path + " " + r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(200);
    }

    private int status(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(token == null ? req : req.header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private String corpoTexto(MockHttpServletRequestBuilder req, String token) throws Exception {
        MvcResult r = mvc.perform(req.header("Authorization", "Bearer " + token)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
