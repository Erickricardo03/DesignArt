package com.designart.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuração 100% por ambiente:
 * NEXUS_MAIL_ENABLED (padrão false), MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM,
 * MAIL_FROM_NAME, MAIL_STARTTLS, MAIL_SSL, APP_PUBLIC_URL. Sem NENHUM valor secreto neste código.
 * Habilitado mas incompleto = a aplicação FALHA ao subir (nunca sobe "meio configurada").
 */
@Configuration
public class MailConfig {

    @Bean
    public EmailSender emailSender(@Value("${nexus.mail.enabled:false}") boolean enabled,
                                   @Value("${nexus.mail.from:}") String from,
                                   @Value("${nexus.mail.from-name:Nexus Design}") String fromName,
                                   @Value("${spring.mail.host:}") String host,
                                   @Value("${app.public-url:}") String publicUrl,
                                   ObjectProvider<JavaMailSender> mailSender) {
        if (!enabled) {
            return new DisabledEmailSender();
        }
        if (host.isBlank() || from.isBlank() || publicUrl.isBlank() || mailSender.getIfAvailable() == null) {
            throw new IllegalStateException("NEXUS_MAIL_ENABLED=true exige MAIL_HOST, MAIL_FROM e APP_PUBLIC_URL configurados.");
        }
        return new SmtpEmailSender(mailSender.getObject(), from, fromName);
    }

    /**
     * Executor do envio (fora da requisição e fora de qualquer transação de banco). Fila limitada:
     * se estiver cheia, o envio é rejeitado e o token é revogado (sem token "abandonado" utilizável).
     * {@code nexus.mail.async=false} (usado nos testes) executa no mesmo thread.
     */
    @Bean(name = "mailExecutor")
    public TaskExecutor mailExecutor(@Value("${nexus.mail.async:true}") boolean async) {
        if (!async) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
