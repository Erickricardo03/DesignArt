package com.designart.mail;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditEvent;
import com.designart.identity.IntegrationTestBase;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import com.designart.token.UserActionToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Consistência transação/e-mail: sem SMTP dentro da transação e sem token válido órfão de e-mail. */
@ExtendWith(OutputCaptureExtension.class)
class MailIntegrationTest extends IntegrationTestBase {

    @Test
    void emailDesabilitado_forgotContinua202SemTokenNemEmail_eConviteRetorna503() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("ml.off@teste.local", Role.USER, t.getId());
        criarUsuario("ml.off.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("ml.off.admin@teste.local");
        MvcResult referencia = forgot("ml.off.fantasma@teste.local");
        emails.setEnabled(false);
        long tokens = tokenRepository.count();
        MvcResult r = forgot("ml.off@teste.local");
        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        assertThat(corpoJson(r).get("message")).isEqualTo(corpoJson(referencia).get("message"));
        assertThat(tokenRepository.count()).isEqualTo(tokens);                      // nenhum token "sem e-mail"
        assertThat(tokenRepository.findByUserIdOrderByIdAsc(u.getId())).isEmpty();
        assertThat(postJson("/api/usuarios", Map.of("email", "ml.off.novo@teste.local", "role", "USER"), admin)
                .getResponse().getStatus()).isEqualTo(503);
        assertThat(tokenRepository.count()).isEqualTo(tokens);
    }

    @Test
    void falhaDeEnvioDoReset_revogaOTokenEAudita_semVazarNada(CapturedOutput output) throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("ml.falha@teste.local", Role.USER, t.getId());
        long antes = maxAuditId();
        emails.setFailing(true);
        MvcResult r = forgot("ml.falha@teste.local");
        assertThat(r.getResponse().getStatus()).isEqualTo(202);                     // resposta continua genérica
        assertThat(emails.all()).isEmpty();

        List<UserActionToken> linhas = tokenRepository.findByUserIdOrderByIdAsc(u.getId());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getRevokedAt()).isNotNull();                        // nenhum token válido sem e-mail
        List<AuditAction> acoes = auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes)
                .map(AuditEvent::getAction).toList();
        assertThat(acoes).contains(AuditAction.PASSWORD_RESET_EMAIL_FAILED);
        assertThat(output.getAll()).doesNotContain("ml.falha@teste.local");         // e-mail do usuário nunca em log
        assertThat(dumpAuditoria()).doesNotContain("falha simulada");
    }

    @Test
    void falhaDeEnvioDoConvite_revogaOTokenEAudita_ePermiteReenviar() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("ml.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("ml.admin@teste.local");
        emails.setFailing(true);
        MvcResult r = postJson("/api/usuarios", Map.of("email", "ml.convite@teste.local", "role", "USER"), admin);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        User pendente = userRepository.findByEmail("ml.convite@teste.local").orElseThrow();
        assertThat(pendente.isPendingInvite()).isTrue();
        List<UserActionToken> linhas = tokenRepository.findByUserIdOrderByIdAsc(pendente.getId());
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getRevokedAt()).isNotNull();
        assertThat(auditRepository.findByTargetUserIdOrderByIdAsc(pendente.getId()).stream().map(AuditEvent::getAction))
                .contains(AuditAction.INVITE_CREATED, AuditAction.INVITE_EMAIL_FAILED);

        // recuperação: com SMTP de volta, "reenviar convite" emite novo token e funciona
        emails.setFailing(false);
        assertThat(executar(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/usuarios/" + pendente.getId() + "/reenviar-convite"), admin).getResponse().getStatus()).isEqualTo(200);
        String token = emails.lastToken("ml.convite@teste.local");
        assertThat(aceitarConvite(token, "Senha-Apos-Falha-2026", "Senha-Apos-Falha-2026").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void emailSoEEnviadoDepoisDoCommit_tokenJaPersistidoQuandoOEmailSai() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("ml.commit@teste.local", Role.USER, t.getId());
        // Ao enviar, o token do link já precisa existir (hash) e estar visível fora da transação.
        var visto = new java.util.concurrent.atomic.AtomicBoolean();
        emails.setListener(m -> {
            String token = com.designart.support.CapturingEmailSender.tokenOf(m);
            visto.set(token != null && tokenRepository.findByTokenHash(com.designart.token.TokenCodec.sha256Hex(token)).isPresent());
        });
        try {
            forgot("ml.commit@teste.local");
        } finally {
            emails.setListener(null);
        }
        assertThat(visto.get()).as("token committado antes do envio").isTrue();
        assertThat(tokenRepository.findByUserIdOrderByIdAsc(u.getId())).hasSize(1);
    }

    @Test
    void segredosDeConfiguracaoNuncaAparecemNosLogsDeFluxo(CapturedOutput output) throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("ml.log@teste.local", Role.USER, t.getId());
        forgot("ml.log@teste.local");
        String token = emails.lastToken("ml.log@teste.local");
        resetar(token, "Senha-Nova-Logs-2026", "Senha-Nova-Logs-2026");
        emails.setFailing(true);
        forgot("ml.log@teste.local");
        String saida = output.getAll();
        assertThat(saida).doesNotContain(token).doesNotContain("Senha-Nova-Logs-2026")
                .doesNotContainIgnoringCase("mail.password").doesNotContainIgnoringCase("MAIL_PASSWORD");
    }
}
