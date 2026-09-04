package com.designart.repository;

import com.designart.model.FotoEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FotoEventoRepository extends JpaRepository<FotoEvento, Long> {
    List<FotoEvento> findByEventoIdOrderByDataUploadDesc(Long eventoId);
}
