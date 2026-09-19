package com.designart.service;

import com.designart.model.User;
import com.designart.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Lockout MODERADO de conta (usa users.failed_logins / users.locked_until, criados na V3).
 * <ul>
 *   <li>a cada 5 falhas consecutivas de senha numa conta existente: bloqueio temporário de 5 min,
 *       depois 10, 20 e no máximo 30 min (5·2^(n-1));</li>
 *   <li>a resposta de login permanece SEMPRE genérica (o bloqueio não é revelado);</li>
 *   <li>login bem-sucedido, redefinição de senha e aceite de convite zeram o contador;</li>
 *   <li>o contador é atualizado em transação INDEPENDENTE (a do login é desfeita ao falhar).</li>
 * </ul>
 * <b>Trade-off contra DoS:</b> um atacante pode bloquear temporariamente a conta da vítima com falhas
 * deliberadas. O teto de 30 min, a recuperação por redefinição de senha (que desbloqueia) e os limites
 * por IP tornam isso pouco vantajoso; um bloqueio permanente seria muito pior. Só falhas de SENHA contam
 * (conta inativa/tenant suspenso não são "palpites").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int LIMIAR_DE_FALHAS = 5;
    static final int BLOQUEIO_BASE_MINUTOS = 5;
    static final int BLOQUEIO_MAXIMO_MINUTOS = 30;

    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now(clock));
    }

    /**
     * Registra uma falha de senha. Retorna o instante de desbloqueio se ESTA falha acionou um bloqueio.
     * Nunca lança: um erro aqui não pode transformar um 401 em 500.
     */
    public Optional<LocalDateTime> registerFailure(User user) {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return tx.execute(status -> {
                userRepository.incrementFailedLogins(user.getId());
                int falhas = userRepository.findFailedLogins(user.getId()).orElse(0);
                if (falhas > 0 && falhas % LIMIAR_DE_FALHAS == 0) {
                    LocalDateTime ate = LocalDateTime.now(clock).plusMinutes(duracaoDoBloqueio(falhas));
                    userRepository.lockUntil(user.getId(), ate);
                    return Optional.of(ate);
                }
                return Optional.<LocalDateTime>empty();
            });
        } catch (RuntimeException e) {
            log.error("Falha ao registrar tentativa de login ({}).", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 5 falhas => 5 min; 10 => 10; 15 => 20; 20 ou mais => 30 (teto). */
    static long duracaoDoBloqueio(int falhas) {
        int nivel = Math.max(1, falhas / LIMIAR_DE_FALHAS);
        long minutos = BLOQUEIO_BASE_MINUTOS * (1L << Math.min(nivel - 1, 10));
        return Math.min(minutos, BLOQUEIO_MAXIMO_MINUTOS);
    }
}
