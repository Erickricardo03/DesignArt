package com.designart.repository;

import com.designart.model.Roteiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoteiroRepository extends JpaRepository<Roteiro, Long> {
    List<Roteiro> findAllByOrderByDataCriacaoDesc();
    List<Roteiro> findByLojaIgnoreCase(String loja);
    List<Roteiro> findByStatus(String status);
}
