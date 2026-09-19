package com.designart.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Ponto ÚNICO de gravação da trilha de auditoria. Controllers e services NÃO
 * montam registros nem SQL: chamam esta API tipada, que só recebe enums,
 * {@link AuditActor}, {@link AuditTarget} e {@link AuditMetadata} — nunca texto
 * livre, body ou headers. IP e User-Agent são obtidos e saneados AQUI, da
 * requisição corrente, não passados por quem chama.
 *
 * <h3>Semântica transacional (decisão explícita)</h3>
 * <ul>
 *   <li>{@link #success} usa {@code Propagation.MANDATORY}: PARTICIPA da mesma
 *       transação da operação de negócio. Se a gravação falhar, a operação inteira
 *       é desfeita; e se a operação for desfeita, o evento também. Nunca há
 *       "operação concluída sem auditoria" nem "auditoria de operação inexistente".
 *       Chamar sem transação aberta é um erro de programação (falha imediata).</li>
 *   <li>{@link #failureIndependent} usa uma transação PRÓPRIA (REQUIRES_NEW): serve
 *       para operações que FALHAM (ex.: LOGIN_FAILURE), cuja transação principal
 *       é desfeita — o evento precisa sobreviver a isso. Como o registro de uma
 *       falha não pode transformar um 401 em 500 nem abrir vetor de negação de
 *       serviço, um erro ao gravar é registrado em log (sem dados sensíveis) e
 *       NÃO interrompe a resposta.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;
    private final ClientIpResolver ipResolver;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    /** Evento de sucesso na MESMA transação da operação (obrigatória). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void success(AuditAction action, AuditActor actor, AuditTarget target, AuditMetadata metadata) {
        repository.save(build(action, AuditOutcome.SUCCESS, actor, target, metadata));
    }

    /**
     * Evento de FALHA em transação independente (sobrevive ao rollback da operação
     * principal). Nunca propaga erro de gravação.
     */
    public void failureIndependent(AuditAction action, AuditActor actor, AuditTarget target, AuditMetadata metadata) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status ->
                    repository.save(build(action, AuditOutcome.FAILURE, actor, target, metadata)));
        } catch (RuntimeException e) {
            // Sem dados sensíveis: só a ação e o tipo da exceção.
            log.error("Falha ao gravar evento de auditoria {} ({}). A operação principal segue.",
                    action, e.getClass().getSimpleName());
        }
    }

    private AuditEvent build(AuditAction action, AuditOutcome outcome, AuditActor actor, AuditTarget target,
                             AuditMetadata metadata) {
        HttpServletRequest request = requisicaoAtual();
        return AuditEvent.create(
                LocalDateTime.now(clock),
                action, outcome, actor, target,
                (metadata == null ? AuditMetadata.EMPTY : metadata).toJson(),
                ipResolver.resolveForAudit(request),
                request == null ? null : AuditSanitizer.userAgent(request.getHeader("User-Agent")));
    }

    private static HttpServletRequest requisicaoAtual() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest() : null;
    }
}
