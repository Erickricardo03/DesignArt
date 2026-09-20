package com.designart.support;

import com.designart.admin.AdminTestBase;
import com.designart.audit.AuditAction;
import com.designart.audit.AuditEvent;
import com.designart.model.User;
import com.designart.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Helpers do Modo Suporte. SUPER_ADMINs e tenants existem só no banco de teste (e-mails em teste.local). */
public abstract class SupportTestBase extends AdminTestBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    public record SuperAdmin(User user, String jwt) {
        public long id() {
            return user.getId();
        }
    }

    protected SuperAdmin novoSuperAdmin() throws Exception {
        String email = "sup.suporte" + SEQ.incrementAndGet() + "@teste.local";
        User u = criarUsuario(email, Role.SUPER_ADMIN, null);
        return new SuperAdmin(u, login(email));
    }

    protected static String motivo(String marca) {
        return "Investigando problema reportado pelo cliente " + marca;
    }

    protected MvcResult iniciar(SuperAdmin sa, Long tenantId, String reason) throws Exception {
        Map<String, Object> corpo = new java.util.HashMap<>();
        corpo.put("tenantId", tenantId);
        corpo.put("reason", reason);
        return postJson("/api/admin/support/sessions", corpo, sa.jwt());
    }

    /** Inicia e devolve o UUID da sessão (falha o teste se não for 201). */
    protected String iniciarOk(SuperAdmin sa, Long tenantId) throws Exception {
        MvcResult r = iniciar(sa, tenantId, motivo("ID" + SEQ.incrementAndGet()));
        assertThat(r.getResponse().getStatus()).as(corpo(r)).isEqualTo(201);
        return corpoJson(r).get("id").asText();
    }

    protected MvcResult elevar(SuperAdmin sa, String sessionId, String reason, Boolean confirm) throws Exception {
        Map<String, Object> corpo = new java.util.HashMap<>();
        corpo.put("reason", reason);
        corpo.put("confirm", confirm);
        return postJson("/api/admin/support/sessions/" + sessionId + "/elevate", corpo, sa.jwt());
    }

    protected String elevarOk(SuperAdmin sa, String sessionId) throws Exception {
        MvcResult r = elevar(sa, sessionId, "Corrigir convite pendente do cliente conforme chamado", true);
        assertThat(r.getResponse().getStatus()).as(corpo(r)).isEqualTo(200);
        return sessionId;
    }

    protected int encerrar(SuperAdmin sa, String sessionId) throws Exception {
        return postJson("/api/admin/support/sessions/" + sessionId + "/end", Map.of(), sa.jwt()).getResponse().getStatus();
    }

    protected JsonNode json(MvcResult r) throws Exception {
        return corpoJson(r);
    }

    protected List<AuditEvent> eventosDesde(long antes) {
        return auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes).toList();
    }

    protected List<AuditAction> acoesDesde(long antes) {
        return eventosDesde(antes).stream().map(AuditEvent::getAction).toList();
    }
}
