package com.designart.mail;

/**
 * Mensagem a enviar. O corpo pode conter o link com o token; por isso {@code toString} NUNCA o imprime
 * (nem destinatário nem conteúdo), evitando vazamento acidental em logs/exceções.
 */
public record EmailMessage(String to, String subject, String textBody, String htmlBody) {

    @Override
    public String toString() {
        return "EmailMessage[REDACTED]";
    }
}
