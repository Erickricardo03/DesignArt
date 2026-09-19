package com.designart.tenant;

import com.designart.repository.TenantScopedRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.support.Repositories;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas estruturais: falham se alguém criar, no futuro, uma entidade com
 * tenant_id acessível por um repository que não filtra por tenant, ou se
 * alterar o V1 (que pertence à Fase 2) para introduzir multitenancy.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
class TenantStructureGuardTest {

    /** Entidades com tenant_id que NÃO são tenant-scoped comuns (ver relatório da Fase 3). */
    private static final Set<String> EXCECOES = Set.of(
            "Tenant", // ESTRUTURAL: é o próprio tenant
            "User",   // tenant_id nullable (SUPER_ADMIN futuro); acesso via UserRepository com métodos ...AndTenantId
            // PLANO DE CONTROLE (Fase 4.4.1): dados comerciais/administrativos POR tenant, mas administrados SÓ pelo
            // SUPER_ADMIN (/api/admin/**) e resolvidos pelo EntitlementService. NÃO são dados de negócio do tenant.
            // O ControlPlaneStructureGuardTest garante que só esses pacotes acessam seus repositories.
            "Subscription", "TenantFeatureOverride", "TenantBranding"
    );

    @Autowired EntityManager entityManager;
    @Autowired ApplicationContext context;

    @Test
    void todaEntidadeComTenantIdSoTemRepositoryTenantScoped() {
        Repositories repositories = new Repositories(context);
        List<String> violacoes = entityManager.getMetamodel().getEntities().stream()
                .filter(e -> temAtributoTenantId(e))
                .filter(e -> !EXCECOES.contains(e.getName()))
                .filter(e -> !repositories.hasRepositoryFor(e.getJavaType())
                        || !TenantScopedRepository.class.isAssignableFrom(
                                repositories.getRepositoryInformationFor(e.getJavaType()).orElseThrow().getRepositoryInterface()))
                .map(EntityType::getName)
                .toList();
        assertThat(violacoes)
                .as("Entidades com tenant_id cujo repository não estende TenantScopedRepository")
                .isEmpty();
    }

    @Test
    void v1PermaneceSemMultitenancyEV2Existe() throws IOException {
        String v1 = new String(getClass().getResourceAsStream("/db/migration/V1__initial_schema.sql").readAllBytes(),
                StandardCharsets.UTF_8).toLowerCase();
        assertThat(v1).doesNotContain("tenant");
        assertThat(getClass().getResource("/db/migration/V2__add_multitenancy.sql")).isNotNull();
    }

    private boolean temAtributoTenantId(EntityType<?> e) {
        return e.getAttributes().stream().anyMatch(a -> a.getName().equals("tenantId"));
    }
}
