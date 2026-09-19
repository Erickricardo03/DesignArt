package com.designart.bootstrap;

import com.designart.audit.AuditAction;
import com.designart.identity.IntegrationTestBase;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bootstrap DESABILITADO (padrão): mesmo com e-mail e hash presentes no ambiente nada é criado. Banco isolado;
 * nenhuma propriedade {@code nexus.bootstrap.enabled} é definida, provando que o DEFAULT é desabilitado.
 */
class SuperAdminBootstrapDisabledIntegrationTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        if (System.getenv("SPRING_DATASOURCE_URL") == null) {
            r.add("spring.datasource.url", () -> "jdbc:h2:mem:bootstrap-disabled;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        }
        r.add("nexus.bootstrap.email", () -> "nao.deve.existir@teste.local");
        r.add("nexus.bootstrap.password-hash", () -> new BCryptPasswordEncoder(10).encode("Senha-Que-Nao-Deve-Ser-Usada-1"));
    }

    @Test
    void padraoEDesabilitado_nenhumSuperAdminEhCriado() {
        assertThat(userRepository.findAll().stream().filter(u -> u.getRole() == Role.SUPER_ADMIN)).isEmpty();
        assertThat(userRepository.findByEmail("nao.deve.existir@teste.local")).isEmpty();
        assertThat(auditRepository.findAllByOrderByIdAsc().stream()
                .filter(e -> e.getAction() == AuditAction.SUPER_ADMIN_BOOTSTRAPPED)).isEmpty();
    }
}
