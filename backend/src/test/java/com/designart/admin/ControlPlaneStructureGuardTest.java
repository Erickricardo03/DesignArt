package com.designart.admin;

import com.designart.billing.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda do PLANO DE CONTROLE: os repositories de assinatura/overrides/branding (entidades com tenant_id que
 * NÃO são dados de negócio do tenant) só podem ser injetados em classes do plano de controle. Assim nenhum
 * service de negócio consegue ler/gravar esses dados aceitando um tenantId arbitrário.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
class ControlPlaneStructureGuardTest {

    private static final Set<Class<?>> RESTRITOS = Set.of(
            SubscriptionRepository.class, TenantFeatureOverrideRepository.class, TenantBrandingRepository.class,
            InvoiceRepository.class, InvoicePaymentRepository.class);
    private static final List<String> PERMITIDOS = List.of(
            "com.designart.admin.", "com.designart.entitlement.", "com.designart.billing.");

    @Autowired ApplicationContext context;

    @Test
    void soOPlanoDeControleAcessaOsRepositoriesRestritos() {
        List<String> violacoes = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null || !type.getName().startsWith("com.designart.")) {
                continue;
            }
            Class<?> real = org.springframework.util.ClassUtils.getUserClass(type);
            for (Constructor<?> c : real.getDeclaredConstructors()) {
                for (Class<?> p : c.getParameterTypes()) {
                    if (RESTRITOS.contains(p) && PERMITIDOS.stream().noneMatch(real.getName()::startsWith)) {
                        violacoes.add(real.getName() + " injeta " + p.getSimpleName());
                    }
                }
            }
        }
        assertThat(violacoes).as("classes fora do plano de controle acessando dados restritos").isEmpty();
    }

    @Test
    void todoEndpointAdminExigeSuperAdminNaClasse() {
        for (String name : context.getBeanNamesForAnnotation(org.springframework.web.bind.annotation.RestController.class)) {
            Class<?> c = org.springframework.util.ClassUtils.getUserClass(context.getType(name));
            var mapping = c.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
            if (mapping != null && java.util.Arrays.stream(mapping.value()).anyMatch(v -> v.startsWith("/api/admin"))) {
                assertThat(c.isAnnotationPresent(com.designart.security.SuperAdminOnly.class))
                        .as(c.getSimpleName() + " (/api/admin) deve ser @SuperAdminOnly").isTrue();
            }
        }
    }

    @Test
    void entitlementServiceEUnicoPontoDeDecisao_semComparacaoDeNomeDePlano() throws Exception {
        // Nenhuma classe de produção fora de admin/ decide por nome/código de plano.
        java.nio.file.Path root = java.nio.file.Path.of("src/main/java/com/designart");
        try (var files = java.nio.file.Files.walk(root)) {
            List<String> ruins = files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.toString().replace('\\', '/').contains("/admin/"))
                    .filter(f -> {
                        try {
                            String src = java.nio.file.Files.readString(f);
                            return src.matches("(?s).*(planCode|getPlan\\(\\)\\.getCode|plan\\.getCode\\(\\)\\.equals|\"PREMIUM\"|\"PRO\"|\"BASIC\").*");
                        } catch (java.io.IOException e) {
                            return false;
                        }
                    }).map(f -> f.getFileName().toString()).toList();
            assertThat(ruins).isEmpty();
        }
    }
}
