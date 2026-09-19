package com.designart.service;

import com.designart.exception.ResourceNotFoundException;
import com.designart.model.Roteiro;
import com.designart.repository.RoteiroRepository;
import com.designart.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoteiroService {

    private final RoteiroRepository roteiroRepository;

    @Transactional(readOnly = true)
    public List<Roteiro> listarTodos(String loja) {
        Long tenantId = TenantContext.require();
        if (loja != null && !loja.isBlank()) {
            return roteiroRepository.findByTenantIdAndLojaIgnoreCase(tenantId, loja);
        }
        return roteiroRepository.findAllByTenantIdOrderByDataCriacaoDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public Roteiro buscarPorId(Long id) {
        return roteiroRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Roteiro não encontrado com ID: " + id));
    }

    @Transactional
    public Roteiro criar(Roteiro roteiro) {
        roteiro.setId(null);
        roteiro.setTenantId(TenantContext.require());
        roteiro.setDataCriacao(LocalDateTime.now());
        if (roteiro.getStatus() == null) {
            roteiro.setStatus("PENDENTE");
        }
        return roteiroRepository.save(roteiro);
    }

    @Transactional
    public Roteiro atualizar(Long id, Roteiro dados) {
        Roteiro roteiro = buscarPorId(id);
        roteiro.setTitulo(dados.getTitulo());
        roteiro.setLoja(dados.getLoja());
        roteiro.setCriadorNome(dados.getCriadorNome());
        roteiro.setDataGravacao(dados.getDataGravacao());
        roteiro.setConteudoScript(dados.getConteudoScript());
        roteiro.setObservacoesSet(dados.getObservacoesSet());
        roteiro.setStatus(dados.getStatus());
        roteiro.setFeito(dados.getFeito());
        return roteiroRepository.save(roteiro);
    }

    @Transactional
    public Roteiro marcarComoConcluido(Long id) {
        Roteiro roteiro = buscarPorId(id);
        boolean novoEstado = !Boolean.TRUE.equals(roteiro.getFeito());
        roteiro.setFeito(novoEstado);
        roteiro.setStatus(novoEstado ? "CONCLUIDO" : "PENDENTE");
        roteiro.setDataConclusao(novoEstado ? LocalDateTime.now() : null);
        return roteiroRepository.save(roteiro);
    }

    @Transactional
    public void deletar(Long id) {
        if (roteiroRepository.deleteByIdAndTenantId(id, TenantContext.require()) == 0) {
            throw new ResourceNotFoundException("Roteiro não encontrado com ID: " + id);
        }
    }
}
