package com.designart.repository;

import com.designart.model.VendaFoto;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VendaFotoRepository extends TenantScopedRepository<VendaFoto> {

    List<VendaFoto> findAllByTenantIdOrderByDataVendaDesc(Long tenantId);

    List<VendaFoto> findTop10ByTenantIdOrderByDataVendaDesc(Long tenantId);

    List<VendaFoto> findByTenantIdAndStatusOrderByDataVendaDesc(Long tenantId, String status);

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM VendaFoto v WHERE v.tenantId = :tenantId AND v.status = 'PAGO' AND v.dataVenda >= :inicioMes")
    BigDecimal sumGanhosNoMes(@Param("tenantId") Long tenantId, @Param("inicioMes") LocalDateTime inicioMes);

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM VendaFoto v WHERE v.tenantId = :tenantId AND v.status = 'PENDENTE'")
    BigDecimal sumAReceber(@Param("tenantId") Long tenantId);

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM VendaFoto v WHERE v.tenantId = :tenantId AND v.status = 'ATRASADO'")
    BigDecimal sumAtrasados(@Param("tenantId") Long tenantId);
}
