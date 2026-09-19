package com.designart.repository;

import com.designart.model.Cliente;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClienteRepository extends TenantScopedRepository<Cliente> {
    List<Cliente> findAllByTenantIdOrderByNomeAsc(Long tenantId);
}
