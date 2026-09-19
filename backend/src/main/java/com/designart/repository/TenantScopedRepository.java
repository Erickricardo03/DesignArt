package com.designart.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Base de TODO repository de entidade TENANT-SCOPED.
 * <p>
 * De propósito NÃO estende {@code JpaRepository}/{@code CrudRepository}: assim
 * {@code findById}, {@code findAll}, {@code deleteById}, {@code count} etc. —
 * que ignoram o tenant — simplesmente não existem nestes repositories, e é
 * impossível chamá-los por engano. Só há acesso filtrado por {@code tenantId}.
 * <p>
 * O {@code tenantId} passado aqui deve vir SEMPRE de
 * {@code TenantContext.require()} (nunca de DTO/parâmetro do cliente).
 */
@NoRepositoryBean
public interface TenantScopedRepository<T> extends Repository<T, Long> {

    /**
     * Uso seguro apenas com entidades novas (id nulo, tenantId definido pelo
     * service) ou carregadas antes via {@link #findByIdAndTenantId}.
     */
    <S extends T> S save(S entity);

    List<T> findAllByTenantId(Long tenantId);

    Optional<T> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByIdAndTenantId(Long id, Long tenantId);

    long countByTenantId(Long tenantId);

    /** Retorna quantos registros foram removidos (0 = não existe neste tenant). */
    long deleteByIdAndTenantId(Long id, Long tenantId);
}
