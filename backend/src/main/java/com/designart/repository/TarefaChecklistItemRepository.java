package com.designart.repository;

import com.designart.model.TarefaChecklistItem;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TarefaChecklistItemRepository extends TenantScopedRepository<TarefaChecklistItem> {
    List<TarefaChecklistItem> findByTenantIdAndTarefaIdOrderByOrdemAsc(Long tenantId, Long tarefaId);
}
