package com.designart.repository;

import com.designart.model.Tarefa;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TarefaRepository extends TenantScopedRepository<Tarefa> {

    List<Tarefa> findAllByTenantIdOrderByDataCriacaoDesc(Long tenantId);

    List<Tarefa> findByTenantIdAndLojaIgnoreCase(Long tenantId, String loja);

    List<Tarefa> findByTenantIdAndStatus(Long tenantId, String status);

    List<Tarefa> findByTenantIdAndPrioridade(Long tenantId, String prioridade);

    @Query("SELECT t FROM Tarefa t WHERE t.tenantId = :tenantId AND t.dataEntrega < :hoje AND t.status <> 'CONCLUIDA'")
    List<Tarefa> findTarefasAtrasadas(@Param("tenantId") Long tenantId, @Param("hoje") LocalDate hoje);

    @Query("SELECT t FROM Tarefa t WHERE t.tenantId = :tenantId AND t.dataEntrega BETWEEN :hoje AND :limite AND t.status <> 'CONCLUIDA'")
    List<Tarefa> findTarefasProximasVencimento(@Param("tenantId") Long tenantId,
                                               @Param("hoje") LocalDate hoje,
                                               @Param("limite") LocalDate limite);

    long countByTenantIdAndStatus(Long tenantId, String status);

    @Query("SELECT t FROM Tarefa t WHERE t.tenantId = :tenantId AND " +
           "(:loja IS NULL OR LOWER(t.loja) LIKE LOWER(CONCAT('%', :loja, '%'))) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:prioridade IS NULL OR t.prioridade = :prioridade) " +
           "ORDER BY t.dataCriacao DESC")
    List<Tarefa> searchTarefas(@Param("tenantId") Long tenantId,
                               @Param("loja") String loja,
                               @Param("status") String status,
                               @Param("prioridade") String prioridade);

    /** Desvincula tarefas de um cliente que está sendo excluído (sempre no mesmo tenant). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Tarefa t SET t.clienteId = NULL WHERE t.tenantId = :tenantId AND t.clienteId = :clienteId")
    int desvincularCliente(@Param("tenantId") Long tenantId, @Param("clienteId") Long clienteId);
}
