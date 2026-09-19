package com.designart.tenant;

import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.repository.TenantRepository;
import com.designart.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Prova de isolamento multi-tenant (Fase 3), de ponta a ponta: HTTP real
 * (MockMvc) -> filtro JWT -> TenantContext -> services -> repositories -> banco.
 * <p>
 * Tenant A e Tenant B (e seus usuários) existem SOMENTE neste banco de teste.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationIntegrationTest {

    static final String SENHA = "senha-de-teste-123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    Tenant tenantA;
    Tenant tenantB;
    String tokenA;
    String tokenB;

    @BeforeAll
    void prepararTenants() throws Exception {
        tenantA = tenantRepository.save(Tenant.builder().name("Tenant A (teste)").slug("tenant-a-teste").build());
        tenantB = tenantRepository.save(Tenant.builder().name("Tenant B (teste)").slug("tenant-b-teste").build());
        criarUsuario("admin.a", com.designart.security.Role.TENANT_ADMIN, tenantA.getId());
        criarUsuario("admin.b", com.designart.security.Role.TENANT_ADMIN, tenantB.getId());
        tokenA = login("admin.a");
        tokenB = login("admin.b");
    }

    // ------------------------------------------------------------------
    // Definição dos recursos testados: cada um é criado por A e por B e
    // depois atacado por A usando IDs de B.
    // ------------------------------------------------------------------
    record Recurso(String nome, String path, Map<String, Object> corpo, Map<String, Object> alteracao,
                   String campoVisivel) {
    }

    static Stream<Recurso> recursos() {
        return Stream.of(
                new Recurso("clientes", "/api/clientes",
                        Map.of("nome", "ORIGINAL"), Map.of("nome", "ADULTERADO"), "nome"),
                new Recurso("tarefas", "/api/tarefas",
                        Map.of("titulo", "ORIGINAL", "loja", "Loja"),
                        Map.of("titulo", "ADULTERADO", "loja", "Loja", "status", "A_FAZER", "prioridade", "MEDIA"),
                        "titulo"),
                new Recurso("eventos", "/api/eventos",
                        Map.of("nome", "ORIGINAL"), Map.of("nome", "ADULTERADO"), "nome"),
                new Recurso("roteiros", "/api/roteiros",
                        Map.of("titulo", "ORIGINAL", "loja", "Loja"),
                        Map.of("titulo", "ADULTERADO", "loja", "Loja", "status", "PENDENTE", "feito", false),
                        "titulo"),
                new Recurso("logos", "/api/logos",
                        Map.of("clienteNome", "ORIGINAL"), null, "clienteNome"),
                new Recurso("vendas", "/api/vendas",
                        Map.of("clienteNome", "ORIGINAL", "valorTotal", 10), null, "clienteNome"),
                new Recurso("despesas", "/api/despesas",
                        Map.of("descricao", "ORIGINAL", "categoria", "Outros", "valor", 5, "dataDespesa", "2026-01-15"),
                        Map.of("descricao", "ADULTERADO", "categoria", "Outros", "valor", 5, "dataDespesa", "2026-01-15",
                                "status", "PAGO"),
                        "descricao"),
                new Recurso("avaliacoes", "/api/avaliacoes",
                        Map.of("clienteNome", "ORIGINAL", "texto", "texto"),
                        Map.of("clienteNome", "ADULTERADO", "texto", "texto"), "clienteNome")
        );
    }

    // ------------------------------------------------------------------
    // 1 e 2: cada tenant acessa os próprios dados
    // ------------------------------------------------------------------
    @ParameterizedTest(name = "{0}: A e B acessam e listam somente os próprios registros")
    @MethodSource("recursos")
    void cadaTenantAcessaSeusProprios(Recurso r) throws Exception {
        long idA = criar(tokenA, r.path(), r.corpo());
        long idB = criar(tokenB, r.path(), r.corpo());
        assertThat(idA).isNotEqualTo(idB);

        assertThat(status(get(r.path() + "/" + idA), tokenA)).isEqualTo(200);
        assertThat(status(get(r.path() + "/" + idB), tokenB)).isEqualTo(200);

        Set<Long> visiveisA = ids(listar(tokenA, r.path()));
        Set<Long> visiveisB = ids(listar(tokenB, r.path()));
        assertThat(visiveisA).contains(idA).doesNotContain(idB);
        assertThat(visiveisB).contains(idB).doesNotContain(idA);
    }

    // ------------------------------------------------------------------
    // 3, 4, 5 e 7: A tentando ler/alterar/excluir por ID um registro de B
    // ------------------------------------------------------------------
    @ParameterizedTest(name = "{0}: A não lê, altera nem exclui registro de B por ID")
    @MethodSource("recursos")
    void tenantANaoAcessaRegistroDeBPorId(Recurso r) throws Exception {
        long idB = criar(tokenB, r.path(), r.corpo());
        String urlB = r.path() + "/" + idB;

        // GET
        assertThat(status(get(urlB), tokenA)).isEqualTo(404);

        // PUT (quando o recurso possui atualização)
        if (r.alteracao() != null) {
            assertThat(status(put(urlB).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(r.alteracao())), tokenA)).isEqualTo(404);
        }

        // DELETE
        assertThat(status(delete(urlB), tokenA)).isEqualTo(404);

        // O registro de B continua existindo e INTACTO.
        MvcResult doB = mvc.perform(get(urlB).header("Authorization", "Bearer " + tokenB)).andReturn();
        assertThat(doB.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(doB.getResponse().getContentAsString()).get(r.campoVisivel()).asText())
                .isEqualTo("ORIGINAL");
    }

    // ------------------------------------------------------------------
    // 8 (+ 7): tenantId / id manipulados no corpo, header ou query string
    // ------------------------------------------------------------------
    @ParameterizedTest(name = "{0}: tenantId/id forjados pelo cliente não trocam de tenant nem sobrescrevem B")
    @MethodSource("recursos")
    void tenantIdEIdForjadosSaoIgnorados(Recurso r) throws Exception {
        long idB = criar(tokenB, r.path(), r.corpo());

        // A envia no corpo o tenantId de B e o id do registro de B.
        Map<String, Object> forjado = new java.util.HashMap<>(r.corpo());
        forjado.put("tenantId", tenantB.getId());
        forjado.put("tenant_id", tenantB.getId());
        forjado.put("id", idB);
        forjado.put(r.campoVisivel(), "FORJADO-POR-A");

        MvcResult res = mvc.perform(post(r.path()).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-Id", tenantB.getId())
                .param("tenantId", String.valueOf(tenantB.getId()))
                .content(json.writeValueAsString(forjado))).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        long idCriado = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Criou um registro NOVO (não sobrescreveu o de B) e ele pertence a A.
        assertThat(idCriado).isNotEqualTo(idB);
        assertThat(status(get(r.path() + "/" + idCriado), tokenA)).isEqualTo(200);
        assertThat(status(get(r.path() + "/" + idCriado), tokenB)).isEqualTo(404);
        assertThat(ids(listar(tokenB, r.path()))).doesNotContain(idCriado);

        // O registro de B não foi tocado.
        MvcResult doB = mvc.perform(get(r.path() + "/" + idB).header("Authorization", "Bearer " + tokenB)).andReturn();
        assertThat(json.readTree(doB.getResponse().getContentAsString()).get(r.campoVisivel()).asText())
                .isEqualTo("ORIGINAL");

        // Header e query string apontando para B não mudam a listagem de A.
        MvcResult listagem = mvc.perform(get(r.path()).header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-Id", tenantB.getId())
                .param("tenantId", String.valueOf(tenantB.getId()))).andReturn();
        assertThat(ids(json.readTree(listagem.getResponse().getContentAsString())))
                .doesNotContain(idB).contains(idCriado);
    }

    // ------------------------------------------------------------------
    // 6: associações entre registros de tenants diferentes
    // ------------------------------------------------------------------
    @Nested
    class Associacoes {

        @Test
        void tarefaDeANaoPodeSerVinculadaAClienteDeB() throws Exception {
            long clienteB = criar(tokenB, "/api/clientes", Map.of("nome", "Cliente de B"));

            // Criação
            assertThat(status(post("/api/tarefas").contentType(MediaType.APPLICATION_JSON).content(
                    json.writeValueAsString(Map.of("titulo", "t", "loja", "l", "clienteId", clienteB))), tokenA))
                    .isEqualTo(404);

            // Atualização
            long tarefaA = criar(tokenA, "/api/tarefas", Map.of("titulo", "t", "loja", "l"));
            assertThat(status(put("/api/tarefas/" + tarefaA).contentType(MediaType.APPLICATION_JSON).content(
                    json.writeValueAsString(Map.of("titulo", "t", "loja", "l", "status", "A_FAZER",
                            "prioridade", "MEDIA", "clienteId", clienteB))), tokenA)).isEqualTo(404);

            // Vínculo legítimo (mesmo tenant) continua funcionando.
            long clienteA = criar(tokenA, "/api/clientes", Map.of("nome", "Cliente de A"));
            assertThat(status(post("/api/tarefas").contentType(MediaType.APPLICATION_JSON).content(
                    json.writeValueAsString(Map.of("titulo", "t", "loja", "l", "clienteId", clienteA))), tokenA))
                    .isEqualTo(200);
        }

        @Test
        void fotoDeANaoPodeSerAdicionadaAEventoDeB() throws Exception {
            long eventoB = criar(tokenB, "/api/eventos", Map.of("nome", "Evento de B"));
            assertThat(status(post("/api/eventos/" + eventoB + "/fotos").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("titulo", "foto"))), tokenA)).isEqualTo(404);
            assertThat(status(get("/api/eventos/" + eventoB), tokenB)).isEqualTo(200);
            MvcResult evB = mvc.perform(get("/api/eventos/" + eventoB).header("Authorization", "Bearer " + tokenB)).andReturn();
            assertThat(json.readTree(evB.getResponse().getContentAsString()).get("fotos")).isEmpty();
        }

        @Test
        void fotoDeBNaoPodeSerExcluidaPorA() throws Exception {
            long eventoB = criar(tokenB, "/api/eventos", Map.of("nome", "Evento de B"));
            long fotoB = criar(tokenB, "/api/eventos/" + eventoB + "/fotos", Map.of("titulo", "foto de B"));
            assertThat(status(delete("/api/eventos/fotos/" + fotoB), tokenA)).isEqualTo(404);
            // Ainda existe para B.
            MvcResult evB = mvc.perform(get("/api/eventos/" + eventoB).header("Authorization", "Bearer " + tokenB)).andReturn();
            assertThat(json.readTree(evB.getResponse().getContentAsString()).get("fotos")).hasSize(1);
            // B consegue excluir a própria.
            assertThat(status(delete("/api/eventos/fotos/" + fotoB), tokenB)).isEqualTo(204);
        }

        @Test
        void eventoCriadoPorAComFotosForjadasNaoAtingeB() throws Exception {
            long eventoB = criar(tokenB, "/api/eventos", Map.of("nome", "Evento de B"));
            long fotoB = criar(tokenB, "/api/eventos/" + eventoB + "/fotos", Map.of("titulo", "foto de B"));

            // A cria um evento embutindo uma "foto" com id da foto de B e tenantId de B.
            Map<String, Object> corpo = Map.of("nome", "Evento de A", "fotos", java.util.List.of(
                    Map.of("id", fotoB, "tenantId", tenantB.getId(), "titulo", "foto forjada")));
            long eventoA = criar(tokenA, "/api/eventos", corpo);

            MvcResult evA = mvc.perform(get("/api/eventos/" + eventoA).header("Authorization", "Bearer " + tokenA)).andReturn();
            JsonNode fotosA = json.readTree(evA.getResponse().getContentAsString()).get("fotos");
            assertThat(fotosA).hasSize(1);
            assertThat(fotosA.get(0).get("id").asLong()).isNotEqualTo(fotoB);

            MvcResult evB = mvc.perform(get("/api/eventos/" + eventoB).header("Authorization", "Bearer " + tokenB)).andReturn();
            JsonNode fotosB = json.readTree(evB.getResponse().getContentAsString()).get("fotos");
            assertThat(fotosB).hasSize(1);
            assertThat(fotosB.get(0).get("titulo").asText()).isEqualTo("foto de B");
        }

        @Test
        void checklistItemDeBNaoPodeSerAlternadoPorA() throws Exception {
            Map<String, Object> tarefaComItem = Map.of("titulo", "t", "loja", "l",
                    "checklist", java.util.List.of(Map.of("descricao", "item")));
            long tarefaA = criar(tokenA, "/api/tarefas", tarefaComItem);
            long tarefaB = criar(tokenB, "/api/tarefas", tarefaComItem);
            long itemA = itemId(tokenA, tarefaA);
            long itemB = itemId(tokenB, tarefaB);

            // A usa a própria tarefa mas o item de B, e a tarefa de B com o item de B.
            assertThat(status(patch("/api/tarefas/" + tarefaA + "/checklist/" + itemB + "/toggle"), tokenA)).isEqualTo(404);
            assertThat(status(patch("/api/tarefas/" + tarefaB + "/checklist/" + itemB + "/toggle"), tokenA)).isEqualTo(404);
            // Status de tarefa alheia também não muda.
            assertThat(status(patch("/api/tarefas/" + tarefaB + "/status").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"CONCLUIDA\"}"), tokenA)).isEqualTo(404);

            MvcResult tB = mvc.perform(get("/api/tarefas/" + tarefaB).header("Authorization", "Bearer " + tokenB)).andReturn();
            JsonNode t = json.readTree(tB.getResponse().getContentAsString());
            assertThat(t.get("status").asText()).isNotEqualTo("CONCLUIDA");
            assertThat(t.get("checklist").get(0).get("concluido").asBoolean()).isFalse();

            // O fluxo legítimo funciona.
            assertThat(status(patch("/api/tarefas/" + tarefaA + "/checklist/" + itemA + "/toggle"), tokenA)).isEqualTo(200);
        }

        @Test
        void excluirClienteDeAnaoAfetaTarefasDeB() throws Exception {
            long clienteA = criar(tokenA, "/api/clientes", Map.of("nome", "Cliente de A"));
            long tarefaA = criar(tokenA, "/api/tarefas", Map.of("titulo", "t", "loja", "l", "clienteId", clienteA));
            assertThat(status(delete("/api/clientes/" + clienteA), tokenA)).isEqualTo(204);
            MvcResult t = mvc.perform(get("/api/tarefas/" + tarefaA).header("Authorization", "Bearer " + tokenA)).andReturn();
            assertThat(json.readTree(t.getResponse().getContentAsString()).get("clienteId").isNull()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // Agregados (dashboard, fluxo de caixa, relatório) só enxergam o próprio tenant
    // ------------------------------------------------------------------
    @Test
    void agregadosSoConsideramOTenantAtual() throws Exception {
        // Tenants exclusivos para que os totais sejam determinísticos.
        Tenant tc = tenantRepository.save(Tenant.builder().name("C (teste)").slug("tenant-c-teste").build());
        Tenant td = tenantRepository.save(Tenant.builder().name("D (teste)").slug("tenant-d-teste").build());
        criarUsuario("admin.c", com.designart.security.Role.TENANT_ADMIN, tc.getId());
        criarUsuario("admin.d", com.designart.security.Role.TENANT_ADMIN, td.getId());
        String tokenC = login("admin.c");
        String tokenD = login("admin.d");

        criar(tokenC, "/api/vendas", Map.of("valorTotal", 100, "status", "PAGO"));
        criar(tokenD, "/api/vendas", Map.of("valorTotal", 999, "status", "PAGO"));
        criar(tokenC, "/api/despesas", Map.of("descricao", "d", "categoria", "c", "valor", 30, "dataDespesa", "2026-01-15"));
        criar(tokenD, "/api/despesas", Map.of("descricao", "d", "categoria", "c", "valor", 500, "dataDespesa", "2026-01-15"));
        criar(tokenC, "/api/tarefas", Map.of("titulo", "so-de-C", "loja", "l"));

        JsonNode caixaC = corpo(get("/api/despesas/fluxo-caixa"), tokenC);
        assertThat(caixaC.get("totalEntradas").decimalValue()).isEqualByComparingTo("100");
        assertThat(caixaC.get("totalSaidas").decimalValue()).isEqualByComparingTo("30");

        JsonNode statsD = corpo(get("/api/dashboard/stats"), tokenD);
        assertThat(statsD.get("totalTarefas").asInt()).isZero();
        JsonNode statsC = corpo(get("/api/dashboard/stats"), tokenC);
        assertThat(statsC.get("totalTarefas").asInt()).isEqualTo(1);

        assertThat(corpo(get("/api/relatorios/mensal"), tokenD)).isEmpty();
        assertThat(corpo(get("/api/relatorios/mensal"), tokenC)).hasSize(1);
        assertThat(corpo(get("/api/vendas/ultimas"), tokenC)).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Usuários
    // ------------------------------------------------------------------
    @Nested
    class Usuarios {

        @Test
        void listagemMostraSoUsuariosDoProprioTenant() throws Exception {
            JsonNode listaA = corpo(get("/api/usuarios"), tokenA);
            Set<String> nomes = new HashSet<>();
            listaA.forEach(u -> nomes.add(u.get("email").asText()));
            assertThat(nomes).contains("admin.a@teste.local").doesNotContain("admin.b@teste.local");
        }

        @Test
        void aNaoAlteraNemExcluiUsuarioDeB() throws Exception {
            long idAdminB = userRepository.findByEmail("admin.b@teste.local").orElseThrow().getId();
            assertThat(status(put("/api/usuarios/" + idAdminB).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nomeCompleto\":\"HACKEADO\",\"ativo\":false}"), tokenA)).isEqualTo(404);
            assertThat(status(delete("/api/usuarios/" + idAdminB), tokenA)).isEqualTo(404);
            User b = userRepository.findByEmail("admin.b@teste.local").orElseThrow();
            assertThat(b.getNomeCompleto()).isNotEqualTo("HACKEADO");
            assertThat(b.getAtivo()).isTrue();
        }

        @Test
        void usuarioCriadoPorAPertenceSempreAoTenantDeA() throws Exception {
            Map<String, Object> corpo = new java.util.HashMap<>();
            corpo.put("email", "novo.de.a@teste.local");
            corpo.put("password", "senha-x-123456");
            corpo.put("role", "USER");
            corpo.put("tenantId", tenantB.getId()); // tentativa de plantar usuário no tenant B
            assertThat(status(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(corpo)), tokenA)).isEqualTo(200);
            assertThat(userRepository.findByEmail("novo.de.a@teste.local").orElseThrow().getTenantId()).isEqualTo(tenantA.getId());
        }

        @Test
        void colaboradorNaoAcessaGestaoDeUsuarios() throws Exception {
            criarUsuario("colab.a", com.designart.security.Role.USER, tenantA.getId());
            String tokenColab = login("colab.a");
            assertThat(status(get("/api/usuarios"), tokenColab)).isEqualTo(403);
        }
    }

    // ------------------------------------------------------------------
    // Autenticação e ciclo de vida do contexto
    // ------------------------------------------------------------------
    @Nested
    class Autenticacao {

        @Test
        void semTokenRetorna401EmTodosOsEndpointsDeNegocio() throws Exception {
            for (String path : new String[]{"/api/clientes", "/api/tarefas", "/api/eventos", "/api/roteiros",
                    "/api/logos", "/api/vendas", "/api/despesas", "/api/avaliacoes", "/api/usuarios",
                    "/api/dashboard/stats", "/api/relatorios/mensal"}) {
                assertThat(status(get(path), null)).as(path).isEqualTo(401);
            }
        }

        @Test
        void tokenInvalidoOuForjadoRetorna401() throws Exception {
            assertThat(status(get("/api/clientes"), "lixo.lixo.lixo")).isEqualTo(401);
            // Token com estrutura válida, assinado com OUTRO segredo.
            String idAdminB = String.valueOf(userRepository.findByEmail("admin.b@teste.local").orElseThrow().getId());
            String forjado = io.jsonwebtoken.Jwts.builder().subject(idAdminB).claim("tv", 0)
                    .claim("tenantId", tenantB.getId())
                    .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                            "outro-segredo-outro-segredo-outro-segredo-outro-segredo".getBytes()))
                    .compact();
            assertThat(status(get("/api/clientes"), forjado)).isEqualTo(401);
        }

        @Test
        void superAdminSemTenantFalhaFechadoNosRecursosDeNegocio() throws Exception {
            // SUPER_ADMIN legítimo (sem tenant): autentica, mas NÃO opera dados de tenant.
            criarUsuario("sem.tenant", com.designart.security.Role.SUPER_ADMIN, null);
            String token = login("sem.tenant");
            assertThat(status(get("/api/clientes"), token)).isEqualTo(403);
            assertThat(status(post("/api/clientes").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nome\":\"x\"}"), token)).isEqualTo(403);
            assertThat(status(get("/api/dashboard/stats"), token)).isEqualTo(403);
            assertThat(status(get("/api/usuarios"), token)).isEqualTo(403);
        }

        @Test
        void tenantNaoVazaEntreRequisicoesNaMesmaThread() throws Exception {
            // MockMvc executa na thread do teste: se o ThreadLocal não fosse
            // limpo, a requisição anônima abaixo herdaria o tenant de A.
            assertThat(status(get("/api/clientes"), tokenA)).isEqualTo(200);
            assertThat(TenantContext.get()).isNull();
            assertThat(status(get("/api/clientes"), null)).isEqualTo(401);
            assertThat(TenantContext.get()).isNull();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private void criarUsuario(String username, com.designart.security.Role role, Long tenantId) {
        userRepository.save(User.builder().email(username + "@teste.local").password(passwordEncoder.encode(SENHA))
                .nomeCompleto(username).role(role).ativo(true).tenantId(tenantId).build());
    }

    private String login(String username) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", username + "@teste.local", "password", SENHA)))).andReturn();
        assertThat(r.getResponse().getStatus()).as("login " + username).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private long criar(String token, String path, Map<String, Object> corpo) throws Exception {
        MvcResult r = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token).content(json.writeValueAsString(corpo))).andReturn();
        assertThat(r.getResponse().getStatus()).as("POST " + path + " -> " + r.getResponse().getContentAsString())
                .isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode listar(String token, String path) throws Exception {
        return corpo(get(path), token);
    }

    private JsonNode corpo(MockHttpServletRequestBuilder req, String token) throws Exception {
        MvcResult r = mvc.perform(token == null ? req : req.header("Authorization", "Bearer " + token)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString());
    }

    private int status(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(token == null ? req : req.header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private Set<Long> ids(JsonNode lista) {
        Set<Long> ids = new HashSet<>();
        lista.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private long itemId(String token, long tarefaId) throws Exception {
        return corpo(get("/api/tarefas/" + tarefaId), token).get("checklist").get(0).get("id").asLong();
    }
}
