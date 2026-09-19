package com.designart.repository;

import com.designart.model.Roteiro;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoteiroRepository extends TenantScopedRepository<Roteiro> {
    List<Roteiro> findAllByTenantIdOrderByDataCriacaoDesc(Long tenantId);
    List<Roteiro> findByTenantIdAndLojaIgnoreCase(Long tenantId, String loja);
    List<Roteiro> findByTenantIdAndStatus(Long tenantId, String status);
}
