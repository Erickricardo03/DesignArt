package com.designart.mail;

/**
 * Abstração de envio de e-mail. Produção: {@link SmtpEmailSender}; e-mail desabilitado:
 * {@link DisabledEmailSender}; testes: implementação que captura em memória.
 */
public interface EmailSender {

    /** {@code false} = não há como entregar e-mail; fluxos que dependem dele NÃO devem gerar tokens. */
    boolean isEnabled();

    /** Envia a mensagem (síncrono). Lança exceção se a entrega falhar. */
    void send(EmailMessage message);
}
