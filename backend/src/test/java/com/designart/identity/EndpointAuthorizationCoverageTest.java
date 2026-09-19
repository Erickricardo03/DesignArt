package com.designart.identity;

import com.designart.security.PublicEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEFAULT NEGADO. Percorre TODOS os handlers Spring MVC da aplicação e falha se
 * um endpoint (novo ou existente):
 * <ul>
 *   <li>não tiver regra explícita de autorização ({@code @PreAuthorize}, direto ou via
 *       meta-anotação como {@code @TenantMember}, no método ou na classe) NEM estiver
 *       marcado {@code @PublicEndpoint};</li>
 *   <li>usar uma expressão fora do conjunto aprovado (ex.: {@code permitAll()});</li>
 *   <li>for público sem constar na lista pequena e documentada abaixo.</li>
 * </ul>
 * E, do lado do servidor, confirma que todo endpoint não público responde 401 a anônimos.
 */
class EndpointAuthorizationCoverageTest extends IntegrationTestBase {

    /** ÚNICOS endpoints públicos aprovados (Fase 4.1 + recuperação/convite da 4.3). Novos públicos exigem aprovação e atualização desta lista. */
    static final Set<String> PUBLICOS_APROVADOS = Set.of(
            "POST /api/auth/login",
            "GET /api/auth/ping",
            "POST /api/auth/forgot-password",
            "POST /api/auth/reset-password",
            "POST /api/auth/accept-invite");

    /** Expressões de autorização aprovadas (as das meta-anotações do pacote security). */
    static final Set<String> EXPRESSOES_APROVADAS = Set.of(
            "hasAnyRole('TENANT_ADMIN','USER')",   // @TenantMember
            "hasRole('TENANT_ADMIN')",             // @TenantAdminOnly
            "hasRole('SUPER_ADMIN')",              // @SuperAdminOnly
            "isAuthenticated()",                   // @AuthenticatedAny
            "hasAuthority('PERM_FINANCEIRO')",     // @RequiresFinanceiro
            "hasAuthority('PERM_EQUIPE')",         // @RequiresEquipe
            "hasAuthority('PERM_CONFIGURACOES')"); // @RequiresConfiguracoes

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private record Endpoint(String metodo, String path, HandlerMethod handler) {
        String assinatura() {
            return metodo + " " + path;
        }
    }

    private List<Endpoint> endpointsDaAplicacao() {
        List<Endpoint> lista = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            if (!hm.getBeanType().getName().startsWith("com.designart.")) {
                continue; // ignora handlers do framework (ex.: /error)
            }
            Set<String> caminhos = e.getKey().getPathPatternsCondition().getPatternValues();
            Set<org.springframework.web.bind.annotation.RequestMethod> metodos = e.getKey().getMethodsCondition().getMethods();
            assertThat(metodos).as("método HTTP explícito em " + hm).isNotEmpty();
            for (String path : caminhos) {
                for (var m : metodos) {
                    lista.add(new Endpoint(m.name(), path, hm));
                }
            }
        }
        return lista;
    }

    private PreAuthorize regra(HandlerMethod hm) {
        PreAuthorize doMetodo = AnnotatedElementUtils.findMergedAnnotation(hm.getMethod(), PreAuthorize.class);
        return doMetodo != null ? doMetodo
                : AnnotatedElementUtils.findMergedAnnotation(hm.getBeanType(), PreAuthorize.class);
    }

    private boolean publico(HandlerMethod hm) {
        return AnnotatedElementUtils.hasAnnotation(hm.getMethod(), PublicEndpoint.class);
    }

    @Test
    void todoEndpointTemRegraExplicitaOuEstaNaListaPublicaAprovada() {
        List<Endpoint> endpoints = endpointsDaAplicacao();
        assertThat(endpoints.size()).as("o teste precisa enxergar os endpoints reais").isGreaterThan(40);

        List<String> semRegra = new ArrayList<>();
        List<String> regraForaDoAprovado = new ArrayList<>();
        List<String> publicoESeguro = new ArrayList<>();
        Set<String> publicosEncontrados = new TreeSet<>();
        List<String> foraDeApi = new ArrayList<>();

        for (Endpoint ep : endpoints) {
            if (!ep.path().startsWith("/api/")) {
                foraDeApi.add(ep.assinatura());
            }
            PreAuthorize regra = regra(ep.handler());
            boolean pub = publico(ep.handler());
            if (pub) {
                publicosEncontrados.add(ep.assinatura());
                if (regra != null) {
                    publicoESeguro.add(ep.assinatura());
                }
            } else if (regra == null) {
                semRegra.add(ep.assinatura());
            } else if (!EXPRESSOES_APROVADAS.contains(regra.value())) {
                regraForaDoAprovado.add(ep.assinatura() + " -> " + regra.value());
            }
        }

        assertThat(semRegra).as("endpoints SEM regra explícita de autorização").isEmpty();
        assertThat(regraForaDoAprovado).as("endpoints com expressão fora do conjunto aprovado").isEmpty();
        assertThat(publicoESeguro).as("endpoint marcado público E com regra (ambíguo)").isEmpty();
        assertThat(foraDeApi).as("endpoints fora de /api/").isEmpty();
        assertThat(publicosEncontrados).as("endpoints públicos != lista aprovada").isEqualTo(new TreeSet<>(PUBLICOS_APROVADOS));
    }

    @Test
    void todoEndpointNaoPublicoResponde401AoAnonimo_ePublicosNaoSaoBloqueadosPeloSecurity() throws Exception {
        List<String> falhas = new ArrayList<>();
        for (Endpoint ep : endpointsDaAplicacao()) {
            String path = ep.path().replaceAll("\\{[^}/]+}", "1");
            MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(HttpMethod.valueOf(ep.metodo()), path);
            if (!ep.metodo().equals("GET") && !ep.metodo().equals("DELETE")) {
                b.contentType(MediaType.APPLICATION_JSON).content("{}");
            }
            int status = mvc.perform(b).andReturn().getResponse().getStatus();
            if (publico(ep.handler())) {
                if (status == 401 || status == 403) {
                    falhas.add("público bloqueado: " + ep.assinatura() + " -> " + status);
                }
            } else if (status != 401) {
                falhas.add("anônimo NÃO recebeu 401: " + ep.assinatura() + " -> " + status);
            }
        }
        assertThat(falhas).isEmpty();
    }

    @Test
    void listaPublicaAprovadaEPequenaEExplicita() {
        assertThat(PUBLICOS_APROVADOS).hasSize(5);
    }
}
