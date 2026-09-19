package com.designart.repository;

import com.designart.model.LogoCliente;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogoClienteRepository extends TenantScopedRepository<LogoCliente> {
    List<LogoCliente> findAllByTenantIdOrderByDataUploadDesc(Long tenantId);
    List<LogoCliente> findByTenantIdAndClienteNomeIgnoreCase(Long tenantId, String clienteNome);
}
