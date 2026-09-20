package com.designart.support;

import com.designart.billing.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas estruturais do Modo Suporte: fail-closed e sem bypass. Se alguém adicionar um endpoint, um serviço ou um
 * repository que fure a allowlist, isolamento ou a separação de billing, estes testes quebram.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
class SupportStructureGuardTest {

    @Autowired ApplicationContext context;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    private static final String SUPPORT_PKG = "com.designart.support.";

    private Map<RequestMappingInfo, HandlerMethod> handlersDeSuporte() {
        Map<RequestMappingInfo, HandlerMethod> out = new LinkedHashMap<>();
        handlerMapping.getHandlerMethods().forEach((info, hm) -> {
            if (hm.getBeanType().getName().startsWith(SUPPORT_PKG)) {
                out.put(info, hm);
            }
        });
        return out;
    }

    private Set<String> paths(RequestMappingInfo info) {
        return info.getPathPatternsCondition().getPatternValues();
    }

    @Test
    void todoHandlerSobTenantEstaNaAllowlistECobertoPeloPortao() {
        AntPathMatcher m = new AntPathMatcher();
        int comAllowlist = 0;
        for (var e : handlersDeSuporte().entrySet()) {
            for (String p : paths(e.getKey())) {
                boolean sobTenant = p.contains("/tenant/");
                boolean anotado = e.getValue().hasMethodAnnotation(SupportAllowed.class);
                if (sobTenant) {
                    assertThat(anotado).as(p + " deve declarar @SupportAllowed").isTrue();
                    assertThat(m.match(SupportAccessInterceptor.PATH_PATTERN, p)).as(p + " deve estar sob o portão").isTrue();
                    comAllowlist++;
                } else {
                    assertThat(anotado).as(p + " (ciclo de vida) não é operação de tenant").isFalse();
                    assertThat(m.match(SupportAccessInterceptor.PATH_PATTERN, p)).isFalse();
                }
            }
        }
        assertThat(comAllowlist).isEqualTo(SupportOperation.values().length);
    }

    @Test
    void cadaOperacaoDaAllowlistTemExatamenteUmHandler_eLeituraEGetEscritaNaoE() {
        Map<SupportOperation, List<String>> porOperacao = new EnumMap<>(SupportOperation.class);
        for (var e : handlersDeSuporte().entrySet()) {
            SupportAllowed a = e.getValue().getMethodAnnotation(SupportAllowed.class);
            if (a == null) {
                continue;
            }
            Set<String> metodos = new TreeSet<>();
            e.getKey().getMethodsCondition().getMethods().forEach(mm -> metodos.add(mm.name()));
            if (a.value().isWrite()) {
                assertThat(metodos).as(a.value() + " é escrita").doesNotContain("GET").isNotEmpty();
            } else {
                assertThat(metodos).as(a.value() + " é leitura").containsExactly("GET");
            }
            porOperacao.computeIfAbsent(a.value(), k -> new ArrayList<>()).add(e.getValue().getMethod().getName());
        }
        for (SupportOperation op : SupportOperation.values()) {
            assertThat(porOperacao.get(op)).as("handler de " + op).hasSize(1);
        }
    }

    @Test
    void allowlistNaoTemOperacaoDeBilling() {
        for (SupportOperation op : SupportOperation.values()) {
            assertThat(op.name()).doesNotContainIgnoringCase("BILLING").doesNotContainIgnoringCase("INVOICE")
                    .doesNotContainIgnoringCase("PAYMENT").doesNotContainIgnoringCase("SUBSCRIPTION")
                    .doesNotContainIgnoringCase("PLAN").doesNotContainIgnoringCase("SUSPEND");
        }
    }

    @Test
    void servicosDeSuporteNaoInjetamBilling_nemRepositoriesDeControle() {
        Set<Class<?>> proibidos = Set.of(InvoiceRepository.class, InvoicePaymentRepository.class, BillingService.class,
                com.designart.admin.AdminBillingService.class, SubscriptionRepository.class, PlanRepository.class,
                TenantBrandingRepository.class, TenantFeatureOverrideRepository.class, com.designart.admin.AdminPlanService.class,
                com.designart.admin.AdminTenantService.class);
        List<String> violacoes = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null) {
                continue;
            }
            Class<?> real = org.springframework.util.ClassUtils.getUserClass(type);
            if (!real.getName().startsWith(SUPPORT_PKG)) {
                continue;
            }
            for (Constructor<?> c : real.getDeclaredConstructors()) {
                for (Class<?> p : c.getParameterTypes()) {
                    if (proibidos.contains(p)) {
                        violacoes.add(real.getSimpleName() + " injeta " + p.getSimpleName());
                    }
                }
            }
        }
        assertThat(violacoes).isEmpty();
    }

    @Test
    void ninguemForaDoPacoteDeSuporteUsaSessaoContextoOuServicosDeSuporte() {
        Set<Class<?>> restritos = Set.of(SupportSessionService.class, SupportTenantService.class, SupportSessionRepository.class,
                SupportAccessInterceptor.class);
        List<String> violacoes = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null || !type.getName().startsWith("com.designart.")) {
                continue;
            }
            Class<?> real = org.springframework.util.ClassUtils.getUserClass(type);
            if (real.getName().startsWith(SUPPORT_PKG)) {
                continue;
            }
            for (Constructor<?> c : real.getDeclaredConstructors()) {
                for (Class<?> p : c.getParameterTypes()) {
                    if (restritos.contains(p)) {
                        violacoes.add(real.getName() + " injeta " + p.getSimpleName());
                    }
                }
            }
        }
        assertThat(violacoes).isEmpty();
    }

    @Test
    void anotacaoSupportAllowedSoExisteNoSupportController() {
        for (var e : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            if (AnnotatedElementUtils.hasAnnotation(hm.getMethod(), SupportAllowed.class)) {
                assertThat(hm.getBeanType()).isEqualTo(SupportController.class);
            }
        }
        assertThat(SupportController.class.isAnnotationPresent(com.designart.security.SuperAdminOnly.class)).isTrue();
    }

    @Test
    void codigoDeNegocioNaoReferenciaOModoSuporte_nemOSuporteTocaTenantContext() throws Exception {
        Path root = Path.of("src/main/java/com/designart");
        List<String> ruins = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String rel = f.toString().replace('\\', '/');
                String src = Files.readString(f);
                boolean pacoteSuporte = rel.contains("/designart/support/");
                boolean observabilidade = rel.contains("/designart/observability/");
                boolean audit = rel.contains("/designart/audit/");
                if (!pacoteSuporte && !observabilidade && !audit && src.contains("com.designart.support.")) {
                    ruins.add(f.getFileName() + " referencia o pacote de suporte");
                }
                if (pacoteSuporte && (src.contains("TenantContext.set") || src.contains("TenantContext.require"))) {
                    ruins.add(f.getFileName() + " mexe no TenantContext");
                }
                // nenhum código de suporte troca o tenant do SUPER_ADMIN nem emite JWT
                if (pacoteSuporte && (src.contains("userRepository.save") || src.contains("userRepository.delete")
                        || src.contains("JwtUtil") || src.contains("generateToken") || src.contains("setTokenVersion"))) {
                    ruins.add(f.getFileName() + " altera identidade ou emite JWT");
                }
            }
        }
        assertThat(ruins).isEmpty();
    }

    @Test
    void controllersDeNegocioContinuamSemAcessoParaSuperAdmin() throws Exception {
        // Nenhum controller de negócio (fora de /api/admin e /api/auth) aceita SUPER_ADMIN: o suporte não abriu exceção global.
        Path root = Path.of("src/main/java/com/designart/controller");
        try (var files = Files.walk(root)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String src = Files.readString(f);
                assertThat(src).as(f.getFileName().toString()).doesNotContain("SUPER_ADMIN").doesNotContain("SuperAdminOnly");
            }
        }
    }
}
