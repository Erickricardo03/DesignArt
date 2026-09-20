package com.designart.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Grava/agrupa incidentes em transação INDEPENDENTE (REQUIRES_NEW): sobrevive ao rollback da requisição que
 * falhou e NUNCA propaga erro (registrar um incidente não pode transformar uma resposta em outro erro). O log de
 * falha do próprio registro traz só o tipo da exceção.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorRecorder {

    /** Dados JÁ sanitizados (sem body/headers/query/mensagem de exceção). */
    public record Signal(Long tenantId, Long supportSessionId, Long userId, String correlationId, String method,
                         String path, int status, ErrorClassifier.Classification classification) {
    }

    /** Trava por fingerprint (faixas): serializa gravações concorrentes DESTA instância; entre instâncias vale o índice único parcial. */
    private final Object[] stripes = java.util.stream.Stream.generate(Object::new).limit(64).toArray();

    private final ApplicationErrorEventRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public void record(Signal s) {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            String fp = ErrorClassifier.fingerprint(s.classification().category(), s.classification().code(), s.method(),
                    s.path(), s.classification().exceptionClass());
            LocalDateTime now = LocalDateTime.now(clock);
            synchronized (stripes[Math.floorMod(fp.hashCode(), stripes.length)]) {
                try {
                    tx.executeWithoutResult(st -> upsert(s, fp, now));
                } catch (DataIntegrityViolationException race) {
                    // Corrida entre instâncias na criação do incidente aberto (índice único parcial): agora ele existe -> incrementa.
                    tx.executeWithoutResult(st -> {
                        if (bump(s, fp, now) == 0) {
                            throw race;
                        }
                    });
                }
            }
        } catch (RuntimeException e) {
            log.error("Falha ao registrar incidente de aplicação ({}). A resposta original segue.", e.getClass().getSimpleName());
        }
    }

    private void upsert(Signal s, String fp, LocalDateTime now) {
        if (bump(s, fp, now) > 0) {
            return;
        }
        ApplicationErrorEvent e = new ApplicationErrorEvent();
        e.setFirstOccurredAt(now);
        e.setOccurredAt(now);
        e.setTenantId(s.tenantId());
        e.setSupportSessionId(s.supportSessionId());
        e.setUserId(s.userId());
        e.setCorrelationId(s.correlationId());
        e.setRequestMethod(s.method());
        e.setRequestPath(s.path());
        e.setHttpStatus(s.status());
        e.setErrorCode(s.classification().code());
        e.setErrorCategory(s.classification().category());
        e.setExceptionClass(s.classification().exceptionClass());
        e.setSafeMessage(s.classification().safeMessage());
        e.setFingerprint(fp);
        e.setOccurrenceCount(1);
        repository.saveAndFlush(e);
    }

    private int bump(Signal s, String fp, LocalDateTime now) {
        return s.tenantId() == null
                ? repository.bumpOpenWithoutTenant(fp, now, s.correlationId(), s.userId(), s.supportSessionId(), s.status())
                : repository.bumpOpenForTenant(s.tenantId(), fp, now, s.correlationId(), s.userId(), s.supportSessionId(), s.status());
    }
}
