package com.designart.token;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserActionTokenRepository extends Repository<UserActionToken, Long> {

    <S extends UserActionToken> S save(S token);

    Optional<UserActionToken> findByTokenHash(String tokenHash);

    List<UserActionToken> findByUserIdOrderByIdAsc(Long userId);

    long count();

    /**
     * CONSUMO ATÔMICO e condicional: só afeta a linha se o token ainda for válido. Sob concorrência,
     * o banco serializa os UPDATEs na mesma linha e a segunda transação reavalia a cláusula WHERE
     * (used_at já preenchido) e afeta 0 linhas. Retorna 1 para o único vencedor.
     */
    @Modifying(flushAutomatically = true)
    @Query("update UserActionToken t set t.usedAt = :now "
            + "where t.tokenHash = :hash and t.purpose = :purpose "
            + "and t.usedAt is null and t.revokedAt is null and t.expiresAt > :now")
    int consume(@Param("hash") String hash, @Param("purpose") ActionTokenPurpose purpose, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("update UserActionToken t set t.revokedAt = :now "
            + "where t.userId = :userId and t.purpose = :purpose and t.usedAt is null and t.revokedAt is null")
    int revokeActive(@Param("userId") Long userId, @Param("purpose") ActionTokenPurpose purpose, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("update UserActionToken t set t.revokedAt = :now "
            + "where t.userId = :userId and t.usedAt is null and t.revokedAt is null")
    int revokeAllActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("update UserActionToken t set t.revokedAt = :now "
            + "where t.id = :id and t.usedAt is null and t.revokedAt is null")
    int revokeById(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Retenção: apaga tokens já expirados há mais que o corte (usados ou não). */
    @Modifying(flushAutomatically = true)
    @Query("delete from UserActionToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
