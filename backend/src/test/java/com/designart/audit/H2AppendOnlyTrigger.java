package com.designart.audit;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Espelho, no H2 de TESTE, da proteção append-only que o PostgreSQL recebe pelo
 * trigger da migration V4: bloqueia UPDATE e DELETE de audit_events. Só é
 * registrado quando o banco é H2 (ver {@link H2AuditTriggerInstaller}); no
 * PostgreSQL a proteção real é a da V4.
 */
public class H2AppendOnlyTrigger implements Trigger {

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("audit_events é append-only: UPDATE/DELETE não permitido", "42501");
    }
}
