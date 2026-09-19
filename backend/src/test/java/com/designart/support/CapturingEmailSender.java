package com.designart.support;

import com.designart.mail.EmailMessage;
import com.designart.mail.EmailSender;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Caixa de entrada" em memória para os testes: NENHUM e-mail real é enviado e o token só é
 * acessível aqui (memória do teste) — nunca por log ou resposta HTTP.
 */
@Component
@Primary
@Profile("test")
public class CapturingEmailSender implements EmailSender {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final List<EmailMessage> mensagens = new CopyOnWriteArrayList<>();
    private volatile boolean enabled = true;
    private volatile boolean failing = false;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void send(EmailMessage message) {
        if (failing) {
            throw new IllegalStateException("falha simulada de SMTP");
        }
        java.util.function.Consumer<EmailMessage> l = listener;
        if (l != null) {
            l.accept(message);
        }
        mensagens.add(message);
    }

    private volatile java.util.function.Consumer<EmailMessage> listener;

    /** Observador chamado no momento do envio (para verificar o estado do banco naquele instante). */
    public void setListener(java.util.function.Consumer<EmailMessage> listener) {
        this.listener = listener;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public void clear() {
        mensagens.clear();
    }

    public List<EmailMessage> all() {
        return new ArrayList<>(mensagens);
    }

    public List<EmailMessage> sentTo(String email) {
        return mensagens.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }

    /** Último e-mail enviado ao endereço, ou {@code null}. */
    public EmailMessage lastTo(String email) {
        List<EmailMessage> l = sentTo(email);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    /** Token do link do último e-mail enviado ao endereço, ou {@code null} se não houver. */
    public String lastToken(String email) {
        EmailMessage m = lastTo(email);
        return m == null ? null : tokenOf(m);
    }

    public static String tokenOf(EmailMessage m) {
        Matcher matcher = TOKEN.matcher(m.textBody());
        return matcher.find() ? matcher.group(1) : null;
    }
}
