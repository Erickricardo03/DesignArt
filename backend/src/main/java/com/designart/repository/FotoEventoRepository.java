package com.designart.repository;

import com.designart.model.FotoEvento;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FotoEventoRepository extends TenantScopedRepository<FotoEvento> {
    List<FotoEvento> findByTenantIdAndEventoIdOrderByDataUploadDesc(Long tenantId, Long eventoId);
}
