package com.designart.service;

import com.designart.dto.RelatorioMensalItemDto;
import com.designart.model.Tarefa;
import com.designart.repository.TarefaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelatorioService {

    private final TarefaRepository tarefaRepository;

    @Transactional(readOnly = true)
    public List<RelatorioMensalItemDto> gerarRelatorioMensal(String loja, Integer mes, Integer ano) {
        List<Tarefa> tarefas = tarefaRepository.findAll();

        return tarefas.stream()
                .filter(t -> {
                    if (loja != null && !loja.isBlank() && !"TODAS".equalsIgnoreCase(loja)) {
                        if (!t.getLoja().equalsIgnoreCase(loja)) return false;
                    }
                    if (mes != null && t.getDataEntrega() != null) {
                        if (t.getDataEntrega().getMonthValue() != mes) return false;
                    }
                    if (ano != null && t.getDataEntrega() != null) {
                        if (t.getDataEntrega().getYear() != ano) return false;
                    }
                    return true;
                })
                .map(t -> {
                    String mesAno = "";
                    if (t.getDataEntrega() != null) {
                        mesAno = String.format("%02d/%d", t.getDataEntrega().getMonthValue(), t.getDataEntrega().getYear());
                    }

                    return RelatorioMensalItemDto.builder()
                            .tarefaId(t.getId())
                            .tituloDemanda(t.getTitulo())
                            .loja(t.getLoja())
                            .criadorNome(t.getCriadorNome() != null ? t.getCriadorNome() : "Lucas Matheus")
                            .participantes(t.getResponsaveis() != null ? new ArrayList<>(t.getResponsaveis()) : List.of())
                            .status(t.getStatus())
                            .prioridade(t.getPrioridade())
                            .dataEntrega(t.getDataEntrega())
                            .percentualConcluido(t.getPercentualConcluido())
                            .mesAno(mesAno)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
