package com.designart.repository;

import com.designart.model.LogoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogoClienteRepository extends JpaRepository<LogoCliente, Long> {
    List<LogoCliente> findAllByOrderByDataUploadDesc();
    List<LogoCliente> findByClienteNomeIgnoreCase(String clienteNome);
}
