package com.designart.repository;

import com.designart.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Sem filtro de tenant de propósito: login/autenticação é feito só pelo
    // username (globalmente único), antes de qualquer tenant ser conhecido.
    // Estes dois métodos são de USO EXCLUSIVO de AuthService,
    // JwtAuthenticationFilter e da checagem de unicidade global de username em
    // UserService — nunca para expor/alterar dados de outros usuários.
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    // Gestão de usuários dentro do painel (tenant-scoped). UserService só usa
    // os métodos abaixo — nunca findAll()/findById()/deleteById().
    List<User> findAllByTenantId(Long tenantId);
    Optional<User> findByIdAndTenantId(Long id, Long tenantId);
    long countByTenantIdAndRole(Long tenantId, String role);
    long deleteByIdAndTenantId(Long id, Long tenantId);
}
