package com.designart.repository;

import com.designart.model.Tarefa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TarefaRepository extends JpaRepository<Tarefa, Long> {

    List<Tarefa> findAllByOrderByDataCriacaoDesc();

    List<Tarefa> findByLojaIgnoreCase(String loja);

    List<Tarefa> findByStatus(String status);

    List<Tarefa> findByPrioridade(String prioridade);

    @Query("SELECT t FROM Tarefa t WHERE t.dataEntrega < :hoje AND t.status <> 'CONCLUIDA'")
    List<Tarefa> findTarefasAtrasadas(@Param("hoje") LocalDate hoje);

    @Query("SELECT t FROM Tarefa t WHERE t.dataEntrega BETWEEN :hoje AND :limite AND t.status <> 'CONCLUIDA'")
    List<Tarefa> findTarefasProximasVencimento(@Param("hoje") LocalDate hoje, @Param("limite") LocalDate limite);

    long countByStatus(String status);

    @Query("SELECT t FROM Tarefa t WHERE " +
           "(:loja IS NULL OR LOWER(t.loja) LIKE LOWER(CONCAT('%', :loja, '%'))) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:prioridade IS NULL OR t.prioridade = :prioridade) " +
           "ORDER BY t.dataCriacao DESC")
    List<Tarefa> searchTarefas(@Param("loja") String loja, 
                               @Param("status") String status, 
                               @Param("prioridade") String prioridade);
}
