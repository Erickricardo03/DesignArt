package com.designart.mail;

/**
 * Padrão quando {@code NEXUS_MAIL_ENABLED=false}. Não envia nada e NÃO registra conteúdo algum
 * (nada de token/link/destinatário em log). Como {@link #isEnabled()} é falso, os fluxos sequer
 * geram tokens que ninguém poderia receber.
 */
public class DisabledEmailSender implements EmailSender {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void send(EmailMessage message) {
        throw new IllegalStateException("Envio de e-mail desabilitado (NEXUS_MAIL_ENABLED=false).");
    }
}
