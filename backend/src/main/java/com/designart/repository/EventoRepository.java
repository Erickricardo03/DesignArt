package com.designart.repository;

import com.designart.model.Evento;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventoRepository extends TenantScopedRepository<Evento> {
    List<Evento> findAllByTenantIdOrderByDataEventoDesc(Long tenantId);
}
