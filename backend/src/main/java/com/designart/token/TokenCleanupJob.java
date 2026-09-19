package com.designart.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Retenção: diariamente remove tokens expirados há mais de 30 dias. O histórico fica no audit_events. */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class TokenCleanupJob {

    static final Duration RETENCAO = Duration.ofDays(30);

    private final ActionTokenService tokens;

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void limpar() {
        int removidos = tokens.purgeExpired(RETENCAO);
        if (removidos > 0) {
            log.info("Limpeza de tokens de ação: {} removido(s).", removidos);
        }
    }
}
