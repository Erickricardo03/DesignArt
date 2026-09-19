package com.designart.mail;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/** Templates escapados, links por configuração e SMTP atrás de interface (sem segredo, sem e-mail real). */
class EmailTemplatesAndConfigTest {

    private final EmailTemplates templates = new EmailTemplates();
    private final PublicLinks links = new PublicLinks("https://app.exemplo.com.br/");

    // ---------------- links ----------------

    @Test
    void linksUsamSomenteAppPublicUrl_semBarraFinalERotasFixas() {
        assertThat(links.resetPasswordUrl("TOKEN")).isEqualTo("https://app.exemplo.com.br/reset-password?token=TOKEN");
        assertThat(links.acceptInviteUrl("TOKEN")).isEqualTo("https://app.exemplo.com.br/accept-invite?token=TOKEN");
        assertThat(new PublicLinks("http://localhost:4200").resetPasswordUrl("T")).isEqualTo("http://localhost:4200/reset-password?token=T");
        assertThat(new PublicLinks("https://exemplo.com/app///").acceptInviteUrl("T")).isEqualTo("https://exemplo.com/app/accept-invite?token=T");
    }

    @Test
    void appPublicUrlInvalidaFalhaAoSubir() {
        for (String ruim : new String[]{"ftp://x.com", "javascript:alert(1)", "//evil.com", "https://", "https://u:p@x.com",
                "https://x.com/?a=1", "https://x.com/#frag", "nao e url"}) {
            assertThatThrownBy(() -> new PublicLinks(ruim)).as(ruim).isInstanceOf(IllegalStateException.class);
        }
        assertThat(new PublicLinks("").isConfigured()).isFalse();
        assertThatThrownBy(() -> new PublicLinks("").resetPasswordUrl("T")).isInstanceOf(IllegalStateException.class);
    }

    // ---------------- templates ----------------

    @Test
    void htmlEscapaTextoDinamico_semHtmlArbitrarioDeTenantOuUsuario() {
        String nomeMalicioso = "<script>alert('x')</script> & \"aspas\" <img src=x onerror=alert(1)>";
        EmailMessage m = templates.invite("a@b.co", nomeMalicioso, "<b>Admin</b>", "Empresa <i>X</i> & Cia", links.acceptInviteUrl("TOK"));
        assertThat(m.htmlBody()).doesNotContain("<script>").doesNotContain("<img src=x").doesNotContain("<b>Admin</b>")
                .doesNotContain("<i>X</i>");
        assertThat(m.htmlBody()).contains("&lt;script&gt;").contains("&amp;").contains("&quot;aspas&quot;");
        assertThat(m.htmlBody()).contains("&lt;b&gt;Admin&lt;/b&gt;").contains("Empresa &lt;i&gt;X&lt;/i&gt; &amp; Cia");
    }

    @Test
    void linkNoHtmlTambemEEscapado() {
        EmailMessage m = templates.passwordReset("a@b.co", "Ana", "https://app.x.com/reset-password?token=A&b=\"><script>");
        assertThat(m.htmlBody()).doesNotContain("\"><script>").contains("&quot;&gt;&lt;script&gt;");
    }

    @Test
    void textoPuroNeutralizaQuebrasDeLinhaEControlesNosNomes() {
        EmailMessage m = templates.passwordReset("a@b.co", "Ana\r\nLinha forjada: clique http://evil\u0000", links.resetPasswordUrl("TOK"));
        String nomeNaSaudacao = m.textBody().split("\n")[0];
        assertThat(nomeNaSaudacao).startsWith("Olá, Ana").doesNotContain("\r").doesNotContain("\u0000");
        assertThat(m.textBody().split("\n")[1]).isEmpty(); // a "linha forjada" não virou parágrafo próprio
    }

    @Test
    void assuntosSaoFixosENaoTemDadoDoUsuarioNemToken() {
        EmailMessage reset = templates.passwordReset("a@b.co", "Nome", links.resetPasswordUrl("TOKENSECRETO"));
        EmailMessage convite = templates.invite("a@b.co", "Nome", "Admin", "Tenant", links.acceptInviteUrl("TOKENSECRETO"));
        EmailMessage aviso = templates.passwordChanged("a@b.co", "Nome");
        for (EmailMessage m : new EmailMessage[]{reset, convite, aviso}) {
            assertThat(m.subject()).doesNotContain("TOKENSECRETO").doesNotContain("Nome").doesNotContain("a@b.co");
        }
        // O aviso de senha alterada não carrega link nem token.
        assertThat(aviso.textBody()).doesNotContain("token=").doesNotContain("http");
        assertThat(aviso.htmlBody()).doesNotContain("token=");
        assertThat(reset.textBody()).contains("30 minutos");
        assertThat(convite.textBody()).contains("72 horas");
    }

    @Test
    void toStringDaMensagemNuncaImprimeCorpoNemDestinatario() {
        EmailMessage m = templates.passwordReset("segredo@x.com", "Ana", links.resetPasswordUrl("TOKENSECRETO"));
        assertThat(m.toString()).doesNotContain("TOKENSECRETO").doesNotContain("segredo@x.com").contains("REDACTED");
    }

    // ---------------- SMTP atrás de interface ----------------

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> p = Mockito.mock(ObjectProvider.class);
        Mockito.when(p.getIfAvailable()).thenReturn(sender);
        Mockito.when(p.getObject()).thenReturn(sender);
        return p;
    }

    @Test
    void mailDesabilitadoNaoEnviaNada_padrao() {
        EmailSender s = new MailConfig().emailSender(false, "", "Nexus", "", "", provider(null));
        assertThat(s).isInstanceOf(DisabledEmailSender.class);
        assertThat(s.isEnabled()).isFalse();
        assertThatThrownBy(() -> s.send(new EmailMessage("a@b.co", "s", "t", "h"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void habilitadoSemConfiguracaoCompletaFalhaAoSubir() {
        MailConfig cfg = new MailConfig();
        JavaMailSender ok = new JavaMailSenderImpl();
        assertThatThrownBy(() -> cfg.emailSender(true, "contato@x.com", "N", "", "https://a.com", provider(ok)))   // sem host
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_HOST");
        assertThatThrownBy(() -> cfg.emailSender(true, "", "N", "smtp.x.com", "https://a.com", provider(ok)))       // sem from
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cfg.emailSender(true, "contato@x.com", "N", "smtp.x.com", "", provider(ok)))       // sem APP_PUBLIC_URL
                .isInstanceOf(IllegalStateException.class);
        assertThat(cfg.emailSender(true, "contato@nexusdevelopment.tech", "Nexus Design", "smtp.x.com", "https://a.com", provider(ok)))
                .isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void smtpMontaMensagemMultipartComRemetenteConfiguradoESemVazarAFalha() throws Exception {
        JavaMailSenderImpl impl = Mockito.spy(new JavaMailSenderImpl());
        Mockito.doNothing().when(impl).send(any(MimeMessage.class));
        SmtpEmailSender sender = new SmtpEmailSender(impl, "contato@nexusdevelopment.tech", "Nexus Design");
        sender.send(new EmailMessage("dest@x.com", "Assunto", "texto", "<p>html</p>"));
        org.mockito.ArgumentCaptor<MimeMessage> cap = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        Mockito.verify(impl).send(cap.capture());
        assertThat(cap.getValue().getFrom()[0].toString()).contains("contato@nexusdevelopment.tech");
        assertThat(cap.getValue().getAllRecipients()[0].toString()).isEqualTo("dest@x.com");

        // Falha do servidor SMTP: a mensagem original (host/usuário/destinatário) NÃO é propagada.
        Mockito.doThrow(new MailSendException("auth falhou em smtp.SEGREDO-HOST.com usuario SEGREDO-USER dest@x.com"))
                .when(impl).send(any(MimeMessage.class));
        assertThatThrownBy(() -> sender.send(new EmailMessage("dest@x.com", "s", "t", "h")))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage() + String.valueOf(e.getCause()))
                        .doesNotContain("SEGREDO-HOST").doesNotContain("SEGREDO-USER").doesNotContain("dest@x.com"));
    }
}
