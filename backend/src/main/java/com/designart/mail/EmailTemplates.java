package com.designart.mail;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * Templates simples e fixos. Todo texto dinâmico (nome da pessoa, do convidante, do tenant) é
 * tratado como TEXTO: no HTML é escapado (nada de HTML/script de tenant ou usuário); no texto puro
 * tem quebras de linha/controles neutralizados. Não há editor de template.
 */
@Component
public class EmailTemplates {

    private static final Pattern CONTROLES = Pattern.compile("[\\p{Cc}\\p{Cf}\\u2028\\u2029]+");
    private static final int MAX_NOME = 100;

    public EmailMessage passwordReset(String to, String nomePessoa, String link) {
        String nome = limpar(nomePessoa);
        String text = saudacao(nome)
                + "Recebemos um pedido para redefinir a senha da sua conta no Nexus Design.\n\n"
                + "Para escolher uma nova senha, abra o link abaixo. Ele é válido por 30 minutos e só pode ser usado uma vez:\n\n"
                + link + "\n\n"
                + "Se você não fez este pedido, ignore este e-mail: sua senha continua a mesma.\n\n"
                + "Nexus Design\n";
        String html = pagina("Redefinição de senha",
                "<p>" + saudacaoHtml(nome) + "</p>"
                        + "<p>Recebemos um pedido para redefinir a senha da sua conta no Nexus Design.</p>"
                        + "<p>Para escolher uma nova senha, use o botão abaixo. O link é válido por <strong>30 minutos</strong> e só pode ser usado uma vez.</p>"
                        + botao(link, "Redefinir minha senha")
                        + "<p style=\"color:#666\">Se você não fez este pedido, ignore este e-mail: sua senha continua a mesma.</p>");
        return new EmailMessage(to, "Redefinição de senha - Nexus Design", text, html);
    }

    public EmailMessage invite(String to, String nomePessoa, String nomeConvidante, String nomeTenant, String link) {
        String nome = limpar(nomePessoa);
        String convidante = limpar(nomeConvidante);
        String tenant = limpar(nomeTenant);
        String quem = convidante.isEmpty() ? "Um administrador" : convidante;
        String onde = tenant.isEmpty() ? "o Nexus Design" : tenant + " no Nexus Design";
        String text = saudacao(nome)
                + quem + " convidou você para acessar " + onde + ".\n\n"
                + "Para ativar sua conta e definir sua senha, abra o link abaixo. Ele é válido por 72 horas e só pode ser usado uma vez:\n\n"
                + link + "\n\n"
                + "Se você não esperava este convite, ignore este e-mail.\n\n"
                + "Nexus Design\n";
        String html = pagina("Convite de acesso",
                "<p>" + saudacaoHtml(nome) + "</p>"
                        + "<p>" + esc(quem) + " convidou você para acessar <strong>" + esc(onde) + "</strong>.</p>"
                        + "<p>Para ativar sua conta e definir sua senha, use o botão abaixo. O link é válido por <strong>72 horas</strong> e só pode ser usado uma vez.</p>"
                        + botao(link, "Ativar minha conta")
                        + "<p style=\"color:#666\">Se você não esperava este convite, ignore este e-mail.</p>");
        return new EmailMessage(to, "Convite para acessar o Nexus Design", text, html);
    }

    /** Aviso de senha alterada (sem link, sem token). */
    public EmailMessage passwordChanged(String to, String nomePessoa) {
        String nome = limpar(nomePessoa);
        String text = saudacao(nome)
                + "A senha da sua conta no Nexus Design acaba de ser alterada.\n\n"
                + "Se foi você, nenhuma ação é necessária. Se não reconhece esta alteração, solicite uma nova redefinição de senha "
                + "imediatamente e avise o administrador da sua empresa.\n\n"
                + "Nexus Design\n";
        String html = pagina("Senha alterada",
                "<p>" + saudacaoHtml(nome) + "</p>"
                        + "<p>A senha da sua conta no Nexus Design acaba de ser alterada.</p>"
                        + "<p>Se foi você, nenhuma ação é necessária. Se não reconhece esta alteração, solicite uma nova redefinição "
                        + "de senha imediatamente e avise o administrador da sua empresa.</p>");
        return new EmailMessage(to, "Sua senha foi alterada - Nexus Design", text, html);
    }

    // ------------------------------------------------------------------ helpers
    private static String limpar(String s) {
        if (s == null) {
            return "";
        }
        String t = CONTROLES.matcher(s).replaceAll(" ").trim();
        return t.length() > MAX_NOME ? t.substring(0, MAX_NOME) : t;
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s);
    }

    private static String saudacao(String nome) {
        return (nome.isEmpty() ? "Olá," : "Olá, " + nome + ".") + "\n\n";
    }

    private static String saudacaoHtml(String nome) {
        return nome.isEmpty() ? "Olá," : "Olá, " + esc(nome) + ".";
    }

    private static String botao(String link, String rotulo) {
        String href = esc(link);
        return "<p><a href=\"" + href + "\" style=\"display:inline-block;background:#4f46e5;color:#fff;padding:12px 22px;"
                + "border-radius:8px;text-decoration:none;font-weight:600\" rel=\"noreferrer\">" + esc(rotulo) + "</a></p>"
                + "<p style=\"color:#666;font-size:13px\">Se o botão não funcionar, copie e cole este endereço no navegador:<br>"
                + "<span style=\"word-break:break-all\">" + href + "</span></p>";
    }

    private static String pagina(String titulo, String corpo) {
        return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><meta name=\"referrer\" content=\"no-referrer\">"
                + "<title>" + esc(titulo) + "</title></head>"
                + "<body style=\"font-family:Arial,Helvetica,sans-serif;color:#222;max-width:560px;margin:0 auto;padding:24px\">"
                + "<h2 style=\"color:#111\">" + esc(titulo) + "</h2>" + corpo
                + "<hr style=\"border:none;border-top:1px solid #ddd;margin:24px 0\">"
                + "<p style=\"color:#888;font-size:12px\">Nexus Design &middot; mensagem automática, não responda.</p>"
                + "</body></html>";
    }
}
