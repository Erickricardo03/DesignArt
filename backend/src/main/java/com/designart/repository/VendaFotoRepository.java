package com.designart.repository;

import com.designart.model.VendaFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VendaFotoRepository extends JpaRepository<VendaFoto, Long> {

    List<VendaFoto> findAllByOrderByDataVendaDesc();

    List<VendaFoto> findTop10ByOrderByDataVendaDesc();

    List<VendaFoto> findByStatusOrderByDataVendaDesc(String status);

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM VendaFoto v WHERE v.status = 'PAGO' AND v.dataVenda >= :inicioMes")
    BigDecimal sumGanhosNoMes(@Param("inicioMes") LocalDateTime inicioMes);

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM VendaFoto v WHERE v.status = 'PENDENTE'")
    BigDecimal sumAReceber();

    @Query("SELECT COALESCE(SUM(v.valorTotal), 0) FROM VendaFoto v WHERE v.status = 'ATRASADO'")
    BigDecimal sumAtrasados();
}
