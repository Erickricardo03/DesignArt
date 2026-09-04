package com.designart.repository;

import com.designart.model.TarefaChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TarefaChecklistItemRepository extends JpaRepository<TarefaChecklistItem, Long> {
    List<TarefaChecklistItem> findByTarefaIdOrderByOrdemAsc(Long tarefaId);
}
