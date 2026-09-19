package com.designart.token;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Ciclo de vida dos tokens de ação. Toda escrita exige transação já aberta (MANDATORY): o token
 * nasce/é consumido/revogado junto com a operação de negócio e a auditoria. Tempo SEMPRE via
 * {@link Clock} (UTC), nunca {@code LocalDateTime.now()}.
 */
@Service
@RequiredArgsConstructor
public class ActionTokenService {

    private final UserActionTokenRepository repository;
    private final Clock clock;

    /** Emite um token novo. Só o hash SHA-256 é gravado; o valor puro volta apenas em {@link IssuedToken}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedToken issue(Long userId, ActionTokenPurpose purpose, Long createdByUserId, String requestedIp) {
        String raw = TokenCodec.generate();
        LocalDateTime now = LocalDateTime.now(clock);
        UserActionToken saved = repository.save(UserActionToken.create(
                userId, purpose, TokenCodec.sha256Hex(raw), now, now.plus(purpose.ttl()), requestedIp, createdByUserId));
        return new IssuedToken(saved.getId(), raw, purpose);
    }

    /** Referência (somente leitura) a um token ainda utilizável, sem consumi-lo. */
    public record TokenRef(Long tokenId, Long userId) {
    }

    /**
     * Procura o token pelo hash e confere finalidade, uso, revogação e expiração. Apenas LEITURA —
     * não é a garantia de uso único (essa é {@link #consume}). Vazio para qualquer motivo de invalidez
     * (o chamador responde sempre de forma genérica).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TokenRef> peek(String rawToken, ActionTokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            return Optional.empty();
        }
        return repository.findByTokenHash(TokenCodec.sha256Hex(rawToken.trim()))
                .filter(t -> t.getPurpose() == purpose)
                .filter(t -> t.isActive(LocalDateTime.now(clock)))
                .map(t -> new TokenRef(t.getId(), t.getUserId()));
    }

    /**
     * Consumo ATÔMICO (UPDATE condicional no banco). {@code true} somente para o único vencedor;
     * replay, corrida perdida, expirado, revogado ou finalidade errada retornam {@code false}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean consume(String rawToken, ActionTokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            return false;
        }
        try {
            return repository.consume(TokenCodec.sha256Hex(rawToken.trim()), purpose, LocalDateTime.now(clock)) == 1;
        } catch (ConcurrencyFailureException e) {
            return false; // perdeu a corrida (conflito de atualização): equivale a token já usado
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeActive(Long userId, ActionTokenPurpose purpose) {
        return repository.revokeActive(userId, purpose, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeAllActive(Long userId) {
        return repository.revokeAllActive(userId, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int revokeById(Long tokenId) {
        return repository.revokeById(tokenId, LocalDateTime.now(clock));
    }

    /** Limpeza de retenção (job agendado): remove tokens expirados há mais de {@code retencao}. */
    @Transactional
    public int purgeExpired(Duration retencao) {
        return repository.deleteExpiredBefore(LocalDateTime.now(clock).minus(retencao));
    }
}
