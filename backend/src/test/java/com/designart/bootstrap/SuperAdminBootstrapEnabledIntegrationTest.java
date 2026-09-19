package com.designart.bootstrap;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditActorType;
import com.designart.audit.AuditEvent;
import com.designart.identity.IntegrationTestBase;
import com.designart.model.User;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Bootstrap HABILITADO em banco isolado e vazio: cria no máximo um SUPER_ADMIN, idempotente, sem vazar credencial. */
class SuperAdminBootstrapEnabledIntegrationTest extends IntegrationTestBase {

    static final String EMAIL = "primeiro.super@teste.local";
    static final String SENHA_BOOT = "Senha-Bootstrap-Offline-2026";
    static final String HASH = new BCryptPasswordEncoder(10).encode(SENHA_BOOT);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // H2 isolado por padrão; se o ambiente informar um banco (validação em PostgreSQL), usa esse (deve estar VAZIO).
        if (System.getenv("SPRING_DATASOURCE_URL") == null) {
            r.add("spring.datasource.url", () -> "jdbc:h2:mem:bootstrap-enabled;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        }
        r.add("nexus.bootstrap.enabled", () -> "true");
        r.add("nexus.bootstrap.email", () -> "  Primeiro.Super@Teste.local ");
        r.add("nexus.bootstrap.password-hash", () -> HASH);
    }

    @Autowired SuperAdminBootstrap bootstrap;

    private long supers() {
        return userRepository.findAll().stream().filter(u -> u.getRole() == Role.SUPER_ADMIN).count();
    }

    @Test
    void criaOPrimeiroSuperAdmin_semTenant_ativo_comHash_eOloginFunciona() throws Exception {
        assertThat(supers()).isEqualTo(1);
        User u = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(u.getTenantId()).as("SUPER_ADMIN nunca pertence a tenant").isNull();
        assertThat(u.getAtivo()).isTrue();
        assertThat(u.getEmailVerifiedAt()).isNotNull();
        assertThat(u.getPassword()).isEqualTo(HASH).startsWith("$2");

        MvcResult login = tentarLogin(EMAIL, SENHA_BOOT);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String jwt = corpoJson(login).get("token").asText();
        assertThat(status(get("/api/admin/plans"), jwt)).isEqualTo(200);
        assertThat(status(get("/api/clientes"), jwt)).as("continua sem acesso a dados de negócio").isEqualTo(403);
    }

    @Test
    void auditaSuperAdminBootstrappedUmaVez_comAtorSystem_semCredencial() {
        List<AuditEvent> ev = auditRepository.findAllByOrderByIdAsc().stream()
                .filter(e -> e.getAction() == AuditAction.SUPER_ADMIN_BOOTSTRAPPED).toList();
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).getActorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(ev.get(0).getTargetEmail()).isEqualTo(EMAIL);
        assertThat(dumpAuditoria()).doesNotContain(HASH).doesNotContain(SENHA_BOOT).doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void idempotente_reexecutarNaoRecriaNemSobrescreve_eSegundoSuperAdminNuncaEhCriado() {
        String hashAntes = userRepository.findByEmail(EMAIL).orElseThrow().getPassword();
        long eventos = auditRepository.count();

        assertThat(bootstrap.bootstrap(true, EMAIL, HASH)).isEqualTo(SuperAdminBootstrap.Result.ALREADY_EXISTS);
        assertThat(bootstrap.bootstrap(true, EMAIL, new BCryptPasswordEncoder(10).encode("Outra-Senha-Offline-9999")))
                .isEqualTo(SuperAdminBootstrap.Result.ALREADY_EXISTS);
        assertThat(bootstrap.bootstrap(true, "segundo.super@teste.local", HASH)).isEqualTo(SuperAdminBootstrap.Result.ALREADY_EXISTS);

        assertThat(supers()).isEqualTo(1);
        assertThat(userRepository.findByEmail("segundo.super@teste.local")).isEmpty();
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getPassword()).as("não sobrescreve").isEqualTo(hashAntes);
        assertThat(auditRepository.count()).as("nenhum evento novo").isEqualTo(eventos);
    }

    @Test
    void configuracaoInvalidaFalhaSemVazarOValor() {
        String segredoNoLugarDoHash = "SenhaEmTextoPuro-Nunca-Deve-Aparecer-123";
        assertThatThrownBy(() -> bootstrap.bootstrap(true, EMAIL, segredoNoLugarDoHash))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(segredoNoLugarDoHash);
        for (String hashRuim : new String[]{null, "", "   ", "$2a$04$" + "a".repeat(53), "$1$abc$def", HASH.substring(0, 40), HASH + "x"}) {
            assertThatThrownBy(() -> bootstrap.bootstrap(true, EMAIL, hashRuim)).isInstanceOf(IllegalStateException.class);
        }
        for (String emailRuim : new String[]{null, "", "nao-e-email", "a@b"}) {
            assertThatThrownBy(() -> bootstrap.bootstrap(true, emailRuim, HASH)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(supers()).isEqualTo(1);
    }

    @Test
    void desabilitadoNaoFazNadaMesmoComCredenciaisPresentes() {
        assertThat(bootstrap.bootstrap(false, EMAIL, HASH)).isEqualTo(SuperAdminBootstrap.Result.DISABLED);
        assertThat(bootstrap.bootstrap(false, null, null)).isEqualTo(SuperAdminBootstrap.Result.DISABLED);
    }

    @Test
    void invarianteTenantIdRole_naoPodeSerViolada() {
        // a própria entidade recusa SUPER_ADMIN com tenant e USER/TENANT_ADMIN sem tenant
        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder().email("inv1@teste.local").password("x")
                .role(Role.SUPER_ADMIN).tenantId(1L).ativo(true).build())).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder().email("inv2@teste.local").password("x")
                .role(Role.TENANT_ADMIN).tenantId(null).ativo(true).build())).isInstanceOf(Exception.class);
    }
}
