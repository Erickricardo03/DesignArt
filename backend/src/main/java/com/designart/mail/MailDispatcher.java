package com.designart.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

/**
 * Despacha o e-mail FORA da requisição e FORA de qualquer transação de banco (o SMTP nunca mantém
 * uma transação aberta). Deve ser chamado somente DEPOIS do commit que criou o token.
 * <p>
 * Consistência: se o envio falhar (ou a fila estiver cheia), a ação de compensação recebida é
 * executada (revogar o token e auditar). Assim não sobra "token válido sem e-mail entregue". Se o
 * processo cair entre o commit e o envio, o token existe mas seu valor puro nunca saiu da memória do
 * processo: ninguém o possui, e ele expira sozinho (30 min / 72 h).
 */
@Slf4j
@Component
public class MailDispatcher {

    private final EmailSender sender;
    private final TaskExecutor executor;

    public MailDispatcher(EmailSender sender, @Qualifier("mailExecutor") TaskExecutor executor) {
        this.sender = sender;
        this.executor = executor;
    }

    public boolean isEnabled() {
        return sender.isEnabled();
    }

    /** Envia e, se falhar, executa {@code aoFalhar} (compensação). Nunca lança para o chamador. */
    public void dispatch(EmailMessage message, Runnable aoFalhar) {
        try {
            executor.execute(() -> {
                try {
                    sender.send(message);
                } catch (RuntimeException e) {
                    // Sem destinatário, assunto, corpo ou token: só o tipo.
                    log.error("Falha ao enviar e-mail transacional ({}). Executando compensação.", e.getClass().getSimpleName());
                    executarCompensacao(aoFalhar);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Fila de e-mail cheia: envio rejeitado. Executando compensação.");
            executarCompensacao(aoFalhar);
        }
    }

    /** Aviso sem token (ex.: "senha alterada"): falha só é registrada. */
    public void dispatchBestEffort(EmailMessage message) {
        dispatch(message, () -> { });
    }

    private void executarCompensacao(Runnable aoFalhar) {
        try {
            aoFalhar.run();
        } catch (RuntimeException e) {
            log.error("Falha na compensação de envio de e-mail ({}).", e.getClass().getSimpleName());
        }
    }
}
