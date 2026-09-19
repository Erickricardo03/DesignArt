package com.designart.service;

import com.designart.exception.ResourceNotFoundException;
import com.designart.model.LogoCliente;
import com.designart.repository.LogoClienteRepository;
import com.designart.tenant.TenantContext;
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
        Long tenantId = TenantContext.require();
        if (clienteNome != null && !clienteNome.isBlank()) {
            return logoClienteRepository.findByTenantIdAndClienteNomeIgnoreCase(tenantId, clienteNome);
        }
        return logoClienteRepository.findAllByTenantIdOrderByDataUploadDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public LogoCliente buscarPorId(Long id) {
        return logoClienteRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Logo não encontrada com ID: " + id));
    }

    @Transactional
    public LogoCliente salvar(LogoCliente logo) {
        logo.setId(null);
        logo.setTenantId(TenantContext.require());
        logo.setDataUpload(LocalDateTime.now());
        return logoClienteRepository.save(logo);
    }

    @Transactional
    public void deletar(Long id) {
        if (logoClienteRepository.deleteByIdAndTenantId(id, TenantContext.require()) == 0) {
            throw new ResourceNotFoundException("Logo não encontrada com ID: " + id);
        }
    }
}
