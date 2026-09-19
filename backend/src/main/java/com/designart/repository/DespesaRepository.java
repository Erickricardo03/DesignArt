package com.designart.repository;

import com.designart.model.Despesa;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DespesaRepository extends TenantScopedRepository<Despesa> {

    List<Despesa> findAllByTenantIdOrderByDataDespesaDesc(Long tenantId);

    List<Despesa> findByTenantIdAndStatus(Long tenantId, String status);

    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM Despesa d WHERE d.tenantId = :tenantId AND d.dataDespesa >= :inicioMes AND d.status = 'PAGO'")
    BigDecimal sumDespesasNoMes(@Param("tenantId") Long tenantId, @Param("inicioMes") LocalDate inicioMes);

    List<Despesa> findByTenantIdAndDataDespesaBetweenOrderByDataDespesaDesc(Long tenantId, LocalDate inicio, LocalDate fim);
}
