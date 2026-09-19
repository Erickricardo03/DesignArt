package com.designart.identity;

import com.designart.model.Tenant;
import com.designart.security.Permission;
import com.designart.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Aplica a MATRIZ DE AUTORIZAÇÃO aprovada, ator por ator, contra os endpoints
 * reais. A autorização real está no backend (não no frontend).
 * <pre>
 *  SUPER_ADMIN  : nunca acessa endpoint tenant-scoped (403)
 *  TENANT_ADMIN : tudo do próprio tenant (permissões implícitas)
 *  USER         : módulos operacionais; financeiro só com FINANCEIRO;
 *                 escrita de avaliações só com CONFIGURACOES; /api/usuarios nunca
 * </pre>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationMatrixIntegrationTest extends IntegrationTestBase {

    enum Ator { SUPER_ADMIN, TENANT_ADMIN, USER_SEM_PERMISSAO, USER_FINANCEIRO, USER_CONFIGURACOES, USER_EQUIPE }

    private final Map<Ator, String> tokens = new LinkedHashMap<>();

    @BeforeAll
    void criarAtores() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("m.super@teste.local", Role.SUPER_ADMIN, null);
        criarUsuario("m.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("m.semperm@teste.local", Role.USER, t.getId());
        criarUsuario("m.fin@teste.local", Role.USER, t.getId(), Permission.FINANCEIRO);
        criarUsuario("m.cfg@teste.local", Role.USER, t.getId(), Permission.CONFIGURACOES);
        criarUsuario("m.eq@teste.local", Role.USER, t.getId(), Permission.EQUIPE);
        tokens.put(Ator.SUPER_ADMIN, login("m.super@teste.local"));
        tokens.put(Ator.TENANT_ADMIN, login("m.admin@teste.local"));
        tokens.put(Ator.USER_SEM_PERMISSAO, login("m.semperm@teste.local"));
        tokens.put(Ator.USER_FINANCEIRO, login("m.fin@teste.local"));
        tokens.put(Ator.USER_CONFIGURACOES, login("m.cfg@teste.local"));
        tokens.put(Ator.USER_EQUIPE, login("m.eq@teste.local"));
    }

    /** Resultado esperado por ator: true = permitido (2xx), false = 403. */
    record Caso(HttpMethod metodo, String path, String corpo,
                boolean superAdmin, boolean admin, boolean semPerm, boolean fin, boolean cfg, boolean eq) {

        boolean esperado(Ator a) {
            return switch (a) {
                case SUPER_ADMIN -> superAdmin;
                case TENANT_ADMIN -> admin;
                case USER_SEM_PERMISSAO -> semPerm;
                case USER_FINANCEIRO -> fin;
                case USER_CONFIGURACOES -> cfg;
                case USER_EQUIPE -> eq;
            };
        }
    }

    // membros do tenant (qualquer usuário do tenant; SUPER_ADMIN nunca)
    private static Caso membro(HttpMethod m, String path, String corpo) {
        return new Caso(m, path, corpo, false, true, true, true, true, true);
    }

    private static Caso financeiro(HttpMethod m, String path, String corpo) {
        return new Caso(m, path, corpo, false, true, false, true, false, false);
    }

    private static Caso configuracoes(HttpMethod m, String path, String corpo) {
        return new Caso(m, path, corpo, false, true, false, false, true, false);
    }

    private static Caso somenteTenantAdmin(HttpMethod m, String path, String corpo) {
        return new Caso(m, path, corpo, false, true, false, false, false, false);
    }

    private static List<Caso> matriz() {
        String venda = "{\"clienteNome\":\"m\",\"valorTotal\":1}";
        String despesa = "{\"descricao\":\"d\",\"categoria\":\"c\",\"valor\":1,\"dataDespesa\":\"2026-01-15\"}";
        String avaliacao = "{\"clienteNome\":\"m\",\"texto\":\"t\"}";
        List<Caso> l = new ArrayList<>();
        // Operacionais: qualquer membro do tenant
        for (String p : List.of("/api/clientes", "/api/tarefas", "/api/eventos", "/api/roteiros", "/api/logos",
                "/api/relatorios/mensal", "/api/dashboard/stats", "/api/avaliacoes")) {
            l.add(membro(HttpMethod.GET, p, null));
        }
        l.add(membro(HttpMethod.POST, "/api/clientes", "{\"nome\":\"m\"}"));
        l.add(membro(HttpMethod.POST, "/api/tarefas", "{\"titulo\":\"t\",\"loja\":\"l\"}"));
        l.add(membro(HttpMethod.POST, "/api/eventos", "{\"nome\":\"m\"}"));
        l.add(membro(HttpMethod.POST, "/api/roteiros", "{\"titulo\":\"t\",\"loja\":\"l\"}"));
        l.add(membro(HttpMethod.POST, "/api/logos", "{\"clienteNome\":\"m\"}"));
        // Financeiro: LEITURA e ESCRITA exigem FINANCEIRO
        for (String p : List.of("/api/despesas", "/api/despesas/fluxo-caixa", "/api/vendas", "/api/vendas/ultimas")) {
            l.add(financeiro(HttpMethod.GET, p, null));
        }
        l.add(financeiro(HttpMethod.POST, "/api/vendas", venda));
        l.add(financeiro(HttpMethod.POST, "/api/despesas", despesa));
        // Escrita de avaliações: CONFIGURACOES
        l.add(configuracoes(HttpMethod.POST, "/api/avaliacoes", avaliacao));
        // Gestão de usuários: somente TENANT_ADMIN
        l.add(somenteTenantAdmin(HttpMethod.GET, "/api/usuarios", null));
        return l;
    }

    private MockHttpServletRequestBuilder req(Caso c) {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(c.metodo(), c.path());
        if (c.corpo() != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(c.corpo());
        }
        return b;
    }

    @Test
    void matrizDeAutorizacaoEAplicadaParaCadaAtor() throws Exception {
        List<String> divergencias = new ArrayList<>();
        for (Caso c : matriz()) {
            for (Ator ator : Ator.values()) {
                int status = status(req(c), tokens.get(ator));
                boolean permitido = status >= 200 && status < 300;
                boolean negado = status == 403;
                boolean esperado = c.esperado(ator);
                if (esperado && !permitido || !esperado && !negado) {
                    divergencias.add(ator + " " + c.metodo() + " " + c.path() + " -> " + status
                            + " (esperado " + (esperado ? "2xx" : "403") + ")");
                }
            }
        }
        assertThat(divergencias).as("divergências da matriz").isEmpty();
    }

    @Test
    void anonimoRecebe401EmTodosOsCasosDaMatriz() throws Exception {
        for (Caso c : matriz()) {
            assertThat(status(req(c), null)).as(c.metodo() + " " + c.path()).isEqualTo(401);
        }
    }

    @Test
    void escritasDeAvaliacaoEFinanceiroSaoNegadasAoUserSemPermissao_incluindoPutEDelete() throws Exception {
        // cria os recursos com o TENANT_ADMIN e tenta alterá-los/excluí-los com USER sem permissão
        long avaliacao = idDe(executar(req(new Caso(HttpMethod.POST, "/api/avaliacoes", "{\"clienteNome\":\"x\",\"texto\":\"t\"}",
                false, true, false, false, true, false)), tokens.get(Ator.TENANT_ADMIN)));
        long despesa = idDe(executar(req(new Caso(HttpMethod.POST, "/api/despesas",
                "{\"descricao\":\"d\",\"categoria\":\"c\",\"valor\":1,\"dataDespesa\":\"2026-01-15\"}",
                false, true, false, true, false, false)), tokens.get(Ator.TENANT_ADMIN)));
        long venda = idDe(executar(req(new Caso(HttpMethod.POST, "/api/vendas", "{\"valorTotal\":1}",
                false, true, false, true, false, false)), tokens.get(Ator.TENANT_ADMIN)));

        String semPerm = tokens.get(Ator.USER_SEM_PERMISSAO);
        assertThat(status(MockMvcRequestBuilders.put("/api/avaliacoes/" + avaliacao).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteNome\":\"h\",\"texto\":\"h\"}"), semPerm)).isEqualTo(403);
        assertThat(status(MockMvcRequestBuilders.delete("/api/avaliacoes/" + avaliacao), semPerm)).isEqualTo(403);
        assertThat(status(MockMvcRequestBuilders.put("/api/despesas/" + despesa).contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"h\",\"categoria\":\"c\",\"valor\":1,\"dataDespesa\":\"2026-01-15\"}"), semPerm)).isEqualTo(403);
        assertThat(status(MockMvcRequestBuilders.delete("/api/despesas/" + despesa), semPerm)).isEqualTo(403);
        assertThat(status(MockMvcRequestBuilders.patch("/api/vendas/" + venda + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAGO\"}"), semPerm)).isEqualTo(403);
        assertThat(status(MockMvcRequestBuilders.delete("/api/vendas/" + venda), semPerm)).isEqualTo(403);
        // Permissões são independentes: FINANCEIRO não abre CONFIGURACOES e vice-versa.
        assertThat(status(MockMvcRequestBuilders.delete("/api/avaliacoes/" + avaliacao), tokens.get(Ator.USER_FINANCEIRO))).isEqualTo(403);
        assertThat(status(MockMvcRequestBuilders.delete("/api/despesas/" + despesa), tokens.get(Ator.USER_CONFIGURACOES))).isEqualTo(403);
        // ...e quem tem a permissão certa consegue.
        assertThat(status(MockMvcRequestBuilders.delete("/api/despesas/" + despesa), tokens.get(Ator.USER_FINANCEIRO))).isEqualTo(204);
        assertThat(status(MockMvcRequestBuilders.delete("/api/avaliacoes/" + avaliacao), tokens.get(Ator.USER_CONFIGURACOES))).isEqualTo(204);
    }

    @Test
    void dashboardOmiteBlocosFinanceirosParaQuemNaoTemFinanceiro() throws Exception {
        // dados financeiros reais no tenant, criados por quem pode
        executar(req(new Caso(HttpMethod.POST, "/api/vendas", "{\"valorTotal\":50,\"status\":\"PAGO\"}",
                false, true, false, true, false, false)), tokens.get(Ator.TENANT_ADMIN));

        JsonNode admin = corpoJson(executar(get("/api/dashboard/stats"), tokens.get(Ator.TENANT_ADMIN)));
        JsonNode fin = corpoJson(executar(get("/api/dashboard/stats"), tokens.get(Ator.USER_FINANCEIRO)));
        for (JsonNode s : List.of(admin, fin)) { // TENANT_ADMIN (implícito) e USER com FINANCEIRO
            assertThat(s.has("ganhosNoMes")).isTrue();
            assertThat(s.has("aReceber")).isTrue();
            assertThat(s.get("ultimasVendas")).isNotEmpty();
        }
        for (Ator a : List.of(Ator.USER_SEM_PERMISSAO, Ator.USER_CONFIGURACOES, Ator.USER_EQUIPE)) {
            JsonNode s = corpoJson(executar(get("/api/dashboard/stats"), tokens.get(a)));
            assertThat(s.has("ganhosNoMes")).as(a + " ganhosNoMes").isFalse();
            assertThat(s.has("aReceber")).as(a + " aReceber").isFalse();
            assertThat(s.has("atrasados")).as(a + " atrasados").isFalse();
            assertThat(s.get("ultimasVendas")).as(a + " ultimasVendas").isEmpty();
            assertThat(s.has("totalTarefas")).isTrue(); // o restante do dashboard segue disponível
        }
    }

    @Test
    void authMeFuncionaParaTodosOsAtoresAutenticados() throws Exception {
        for (Ator a : Ator.values()) {
            assertThat(status(get("/api/auth/me"), tokens.get(a))).as(a.name()).isEqualTo(200);
        }
    }

    private long idDe(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).as(corpo(r)).isEqualTo(200);
        return corpoJson(r).get("id").asLong();
    }
}
