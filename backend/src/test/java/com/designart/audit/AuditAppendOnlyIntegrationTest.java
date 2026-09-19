package com.designart.audit;

import com.designart.identity.IntegrationTestBase;
import com.designart.model.Tenant;
import com.designart.security.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * audit_events é APPEND-ONLY. Estes testes rodam nos DOIS bancos:
 * <ul>
 *   <li>H2 (suíte normal): a proteção equivalente é o trigger de TESTE
 *       ({@link H2AuditTriggerInstaller}) + os bloqueios da aplicação;</li>
 *   <li>PostgreSQL (suíte executada contra Postgres real): a proteção verificada é a
 *       REAL, dos triggers da migration V4 (UPDATE, DELETE e TRUNCATE).</li>
 * </ul>
 */
class AuditAppendOnlyIntegrationTest extends IntegrationTestBase {

    @Autowired PlatformTransactionManager txManager;
    @Autowired DataSource dataSource;
    @PersistenceContext EntityManager em;

    private long criarEventoPelaAplicacao() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("append.only." + System.nanoTime() + "@teste.local", Role.USER, t.getId());
        long antes = maxAuditId();
        // O login gera LOGIN_SUCCESS: a aplicação CONSEGUE inserir.
        String email = userRepository.findAllByTenantId(t.getId()).get(0).getEmail();
        login(email);
        assertThat(maxAuditId()).isGreaterThan(antes);
        return maxAuditId();
    }

    private boolean ehPostgres() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        }
    }

    @Test
    void aplicacaoConsegueInserir_eOsDadosFicamLegiveis() throws Exception {
        long id = criarEventoPelaAplicacao();
        Long contagem = jdbc.queryForObject("select count(*) from audit_events where id = ?", Long.class, id);
        assertThat(contagem).isEqualTo(1L);
        // INSERT direto também é permitido (só UPDATE/DELETE/TRUNCATE são bloqueados).
        int inseridos = jdbc.update("insert into audit_events(occurred_at, actor_type, action, outcome) values (?,?,?,?)",
                Timestamp.valueOf(LocalDateTime.now()), "SYSTEM", "LOGIN_SUCCESS", "SUCCESS");
        assertThat(inseridos).isEqualTo(1);
    }

    @Test
    void updateDiretoNoBancoEBloqueado() throws Exception {
        long id = criarEventoPelaAplicacao();
        String antes = jdbc.queryForObject("select outcome from audit_events where id = ?", String.class, id);
        assertThatThrownBy(() -> jdbc.update("update audit_events set outcome = 'FAILURE' where id = ?", id))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update audit_events set metadata = 'adulterado', actor_email = 'fake@x.com'"))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select outcome from audit_events where id = ?", String.class, id)).isEqualTo(antes);
    }

    @Test
    void deleteDiretoNoBancoEBloqueado() throws Exception {
        long id = criarEventoPelaAplicacao();
        assertThatThrownBy(() -> jdbc.update("delete from audit_events where id = ?", id))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_events")).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void truncateEBloqueadoNoPostgres() throws Exception {
        Assumptions.assumeTrue(ehPostgres(), "TRUNCATE só é protegido por trigger no PostgreSQL (V4)");
        criarEventoPelaAplicacao();
        long total = jdbc.queryForObject("select count(*) from audit_events", Long.class);
        assertThatThrownBy(() -> jdbc.execute("truncate table audit_events")).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class)).isEqualTo(total);
    }

    @Test
    void entidadeNaoPodeSerAlteradaNemRemovidaPelaAplicacao() throws Exception {
        long id = criarEventoPelaAplicacao();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // Alterar um campo por reflexão: o Hibernate ignora (colunas não atualizáveis) e nada muda no banco.
        tx.executeWithoutResult(s -> {
            AuditEvent e = em.find(AuditEvent.class, id);
            ReflectionTestUtils.setField(e, "outcome", AuditOutcome.FAILURE);
            ReflectionTestUtils.setField(e, "actorEmail", "adulterado@teste.local");
            em.flush();
            em.clear();
        });
        AuditEvent relido = auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() == id).findFirst().orElseThrow();
        assertThat(relido.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(relido.getActorEmail()).isNotEqualTo("adulterado@teste.local");

        // Remover pela aplicação: bloqueado (@PreRemove) e o evento continua lá.
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            em.remove(em.find(AuditEvent.class, id));
            em.flush();
        })).hasStackTraceContaining("append-only");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void limitesDeTamanhoValemNoBanco() {
        String metadataGrande = "x".repeat(AuditMetadata.MAX_LENGTH + 1);
        assertThatThrownBy(() -> jdbc.update("insert into audit_events(occurred_at, actor_type, action, outcome, metadata) values (?,?,?,?,?)",
                Timestamp.valueOf(LocalDateTime.now()), "SYSTEM", "LOGIN_SUCCESS", "SUCCESS", metadataGrande))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into audit_events(occurred_at, actor_type, action, outcome, user_agent) values (?,?,?,?,?)",
                Timestamp.valueOf(LocalDateTime.now()), "SYSTEM", "LOGIN_SUCCESS", "SUCCESS", "u".repeat(201)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into audit_events(occurred_at, actor_type, action, outcome, request_ip) values (?,?,?,?,?)",
                Timestamp.valueOf(LocalDateTime.now()), "SYSTEM", "LOGIN_SUCCESS", "SUCCESS", "1".repeat(46)))
                .isInstanceOf(DataAccessException.class);
        // No limite exato é aceito.
        assertThat(jdbc.update("insert into audit_events(occurred_at, actor_type, action, outcome, user_agent, request_ip) values (?,?,?,?,?,?)",
                Timestamp.valueOf(LocalDateTime.now()), "SYSTEM", "LOGIN_SUCCESS", "SUCCESS", "u".repeat(200), "1".repeat(45))).isEqualTo(1);
    }
}
