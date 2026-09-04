package com.designart.repository;

import com.designart.model.Despesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    List<Despesa> findAllByOrderByDataDespesaDesc();

    List<Despesa> findByStatus(String status);

    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d WHERE d.dataDespesa >= :inicioMes AND d.status = 'PAGO'")
    BigDecimal sumDespesasNoMes(@Param("inicioMes") LocalDate inicioMes);

    List<Despesa> findByDataDespesaBetweenOrderByDataDespesaDesc(LocalDate inicio, LocalDate fim);
}
