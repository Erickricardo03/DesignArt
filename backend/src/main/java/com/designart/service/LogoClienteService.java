package com.designart.service;

import com.designart.model.LogoCliente;
import com.designart.repository.LogoClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LogoClienteService {

    private final LogoClienteRepository logoClienteRepository;

    @Transactional(readOnly = true)
    public List<LogoCliente> listarTodos(String clienteNome) {
        if (clienteNome != null && !clienteNome.isBlank()) {
            return logoClienteRepository.findByClienteNomeIgnoreCase(clienteNome);
        }
        return logoClienteRepository.findAllByOrderByDataUploadDesc();
    }

    @Transactional(readOnly = true)
    public LogoCliente buscarPorId(Long id) {
        return logoClienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Logo não encontrada com ID: " + id));
    }

    @Transactional
    public LogoCliente salvar(LogoCliente logo) {
        logo.setDataUpload(LocalDateTime.now());
        return logoClienteRepository.save(logo);
    }

    @Transactional
    public void deletar(Long id) {
        logoClienteRepository.deleteById(id);
    }
}
