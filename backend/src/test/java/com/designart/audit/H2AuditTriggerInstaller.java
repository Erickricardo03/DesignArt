package com.designart.audit;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Registra o trigger append-only no H2 de teste (o schema é criado pelo Hibernate,
 * sem passar pelo Flyway/V4). Não faz nada em PostgreSQL. Só existe no classpath
 * de testes.
 */
@Component
public class H2AuditTriggerInstaller implements ApplicationRunner {

    private final DataSource dataSource;

    public H2AuditTriggerInstaller(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String produto;
        try (Connection c = dataSource.getConnection()) {
            produto = c.getMetaData().getDatabaseProductName();
        }
        if (!"H2".equalsIgnoreCase(produto)) {
            return;
        }
        new JdbcTemplate(dataSource).execute(
                "CREATE TRIGGER IF NOT EXISTS audit_events_block_update_delete BEFORE UPDATE, DELETE ON audit_events "
                        + "FOR EACH ROW CALL \"com.designart.audit.H2AppendOnlyTrigger\"");
    }
}
