package com.designart.service;

import com.designart.model.Roteiro;
import com.designart.repository.RoteiroRepository;
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
        if (loja != null && !loja.isBlank()) {
            return roteiroRepository.findByLojaIgnoreCase(loja);
        }
        return roteiroRepository.findAllByOrderByDataCriacaoDesc();
    }

    @Transactional(readOnly = true)
    public Roteiro buscarPorId(Long id) {
        return roteiroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Roteiro não encontrado com ID: " + id));
    }

    @Transactional
    public Roteiro criar(Roteiro roteiro) {
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
        roteiroRepository.deleteById(id);
    }
}
