package com.designart.service;

import com.designart.exception.ResourceNotFoundException;
import com.designart.model.Avaliacao;
import com.designart.repository.AvaliacaoRepository;
import com.designart.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;

    @Transactional(readOnly = true)
    public List<Avaliacao> listarTodas(boolean apenasAtivas) {
        Long tenantId = TenantContext.require();
        if (apenasAtivas) {
            return avaliacaoRepository.findByTenantIdAndAtivoTrueOrderByDataCriacaoDesc(tenantId);
        }
        return avaliacaoRepository.findAllByTenantIdOrderByDataCriacaoDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public Avaliacao buscarPorId(Long id) {
        return avaliacaoRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação não encontrada com ID: " + id));
    }

    @Transactional
    public Avaliacao criar(Avaliacao avaliacao) {
        avaliacao.setId(null);
        avaliacao.setTenantId(TenantContext.require());
        avaliacao.setDataCriacao(LocalDateTime.now());
        if (avaliacao.getAtivo() == null) {
            avaliacao.setAtivo(true);
        }
        return avaliacaoRepository.save(avaliacao);
    }

    @Transactional
    public Avaliacao atualizar(Long id, Avaliacao dados) {
        Avaliacao existente = buscarPorId(id);
        existente.setClienteNome(dados.getClienteNome());
        existente.setCargoEmpresa(dados.getCargoEmpresa());
        existente.setTexto(dados.getTexto());
        existente.setNota(dados.getNota());
        if (dados.getAtivo() != null) {
            existente.setAtivo(dados.getAtivo());
        }
        return avaliacaoRepository.save(existente);
    }

    @Transactional
    public void deletar(Long id) {
        if (avaliacaoRepository.deleteByIdAndTenantId(id, TenantContext.require()) == 0) {
            throw new ResourceNotFoundException("Avaliação não encontrada com ID: " + id);
        }
    }
}
