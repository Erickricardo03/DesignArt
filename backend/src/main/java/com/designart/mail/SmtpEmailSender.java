package com.designart.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Envio real via SMTP (JavaMail). Toda a configuração (host, porta, usuário, senha, TLS) vem do
 * ambiente via {@code spring.mail.*} — nada é fixo no código. Esta classe nunca registra senha,
 * destinatário, assunto ou corpo; falhas propagam SEM a mensagem original do servidor SMTP.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender, String from, String fromName) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mime);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            // Não propaga a causa (pode conter host/usuário/destinatário): só o tipo.
            throw new IllegalStateException("Falha no envio SMTP (" + e.getClass().getSimpleName() + ").");
        }
    }
}
