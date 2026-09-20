package com.designart.support;

import com.designart.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Plano de controle: só o pacote de suporte a utiliza (ver SupportStructureGuardTest). */
public interface SupportSessionRepository extends JpaRepository<SupportSession, Long>, JpaSpecificationExecutor<SupportSession> {

    Optional<SupportSession> findByPublicId(UUID publicId);

    Optional<SupportSession> findFirstBySuperAdminUserIdAndEndedAtIsNull(Long superAdminUserId);

    /** Trava a linha (FOR UPDATE) para serializar mutações da mesma sessão (encerrar/elevar). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SupportSession s where s.publicId = :publicId")
    Optional<SupportSession> findByPublicIdForUpdate(UUID publicId);

    /**
     * Trava a linha do SUPER_ADMIN para serializar a abertura de sessões concorrentes dele (política: no máximo
     * uma ativa). Complementa o índice único parcial do PostgreSQL (o H2 de teste não o possui).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> lockSuperAdmin(Long userId);

    /** Encerra (ended_at = expires_at) as sessões vencidas e não encerradas do SUPER_ADMIN. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupportSession s set s.endedAt = s.expiresAt where s.superAdminUserId = :userId and s.endedAt is null and s.expiresAt <= :now")
    int closeExpired(Long userId, LocalDateTime now);
}
