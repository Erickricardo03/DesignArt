package com.designart.repository;

import com.designart.model.Avaliacao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvaliacaoRepository extends TenantScopedRepository<Avaliacao> {
    List<Avaliacao> findAllByTenantIdOrderByDataCriacaoDesc(Long tenantId);
    List<Avaliacao> findByTenantIdAndAtivoTrueOrderByDataCriacaoDesc(Long tenantId);
}
