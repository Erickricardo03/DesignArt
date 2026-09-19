package com.designart.repository;

import com.designart.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Sem filtro de tenant de propósito: identidade/autenticação acontece antes de
    // qualquer tenant ser conhecido. Estes métodos (e findById) são de USO EXCLUSIVO
    // de AuthService, JwtAuthenticationFilter e da checagem de unicidade GLOBAL de
    // e-mail — nunca para expor/alterar dados de outros usuários. O e-mail recebido
    // aqui já deve estar normalizado (EmailAddress).
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Gestão de usuários dentro do painel (tenant-scoped). UserService só usa
    // os métodos abaixo — nunca findAll()/findById()/deleteById().
    List<User> findAllByTenantId(Long tenantId);

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    long countByTenantIdAndRoleAndAtivoTrue(Long tenantId, com.designart.security.Role role);

    long deleteByIdAndTenantId(Long id, Long tenantId);

    // ---- Lockout de login (Fase 4.3). Atualizações ATÔMICAS por id (não passam pelos callbacks da entidade). ----
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update User u set u.failedLogins = u.failedLogins + 1 where u.id = :id")
    int incrementFailedLogins(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select u.failedLogins from User u where u.id = :id")
    Optional<Integer> findFailedLogins(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update User u set u.lockedUntil = :until where u.id = :id")
    int lockUntil(@org.springframework.data.repository.query.Param("id") Long id,
                  @org.springframework.data.repository.query.Param("until") java.time.LocalDateTime until);
}
