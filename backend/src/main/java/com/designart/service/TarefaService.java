package com.designart.service;

import com.designart.dto.ChecklistItemDto;
import com.designart.dto.TarefaDto;
import com.designart.model.Tarefa;
import com.designart.model.TarefaChecklistItem;
import com.designart.repository.TarefaChecklistItemRepository;
import com.designart.repository.TarefaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TarefaService {

    private final TarefaRepository tarefaRepository;
    private final TarefaChecklistItemRepository checklistItemRepository;

    @Transactional(readOnly = true)
    public List<TarefaDto> listarTodas(String loja, String status, String prioridade) {
        List<Tarefa> tarefas;
        if ((loja != null && !loja.isBlank()) || (status != null && !status.isBlank()) || (prioridade != null && !prioridade.isBlank())) {
            tarefas = tarefaRepository.searchTarefas(
                    (loja != null && !loja.isBlank()) ? loja : null,
                    (status != null && !status.isBlank()) ? status : null,
                    (prioridade != null && !prioridade.isBlank()) ? prioridade : null
            );
        } else {
            tarefas = tarefaRepository.findAllByOrderByDataCriacaoDesc();
        }
        return tarefas.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TarefaDto buscarPorId(Long id) {
        Tarefa tarefa = tarefaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada com ID: " + id));
        return toDto(tarefa);
    }

    @Transactional
    public TarefaDto criar(TarefaDto dto) {
        Tarefa tarefa = Tarefa.builder()
                .titulo(dto.getTitulo())
                .descricao(dto.getDescricao())
                .briefing(dto.getBriefing())
                .loja(dto.getLoja())
                .clienteId(dto.getClienteId())
                .status(dto.getStatus() != null ? dto.getStatus() : "A_FAZER")
                .prioridade(dto.getPrioridade() != null ? dto.getPrioridade() : "MEDIA")
                .dataGravacao(dto.getDataGravacao())
                .dataEntrega(dto.getDataEntrega() != null ? dto.getDataEntrega() : LocalDate.now().plusDays(5))
                .criadorNome(dto.getCriadorNome() != null ? dto.getCriadorNome() : "Admin")
                .responsaveis(dto.getResponsaveis() != null ? new ArrayList<>(dto.getResponsaveis()) : new ArrayList<>())
                .dataCriacao(LocalDateTime.now())
                .build();

        if (dto.getChecklist() != null && !dto.getChecklist().isEmpty()) {
            List<TarefaChecklistItem> itens = new ArrayList<>();
            for (int i = 0; i < dto.getChecklist().size(); i++) {
                ChecklistItemDto itemDto = dto.getChecklist().get(i);
                itens.add(TarefaChecklistItem.builder()
                        .descricao(itemDto.getDescricao())
                        .concluido(Boolean.TRUE.equals(itemDto.getConcluido()))
                        .ordem(itemDto.getOrdem() != null ? itemDto.getOrdem() : i + 1)
                        .tarefa(tarefa)
                        .build());
            }
            tarefa.setChecklist(itens);
        }

        Tarefa saved = tarefaRepository.save(tarefa);
        return toDto(saved);
    }

    @Transactional
    public TarefaDto atualizar(Long id, TarefaDto dto) {
        Tarefa tarefa = tarefaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada com ID: " + id));

        tarefa.setTitulo(dto.getTitulo());
        tarefa.setDescricao(dto.getDescricao());
        tarefa.setBriefing(dto.getBriefing());
        tarefa.setLoja(dto.getLoja());
        if (dto.getClienteId() != null) {
            tarefa.setClienteId(dto.getClienteId());
        }
        tarefa.setStatus(dto.getStatus());
        tarefa.setPrioridade(dto.getPrioridade());
        tarefa.setDataGravacao(dto.getDataGravacao());
        tarefa.setDataEntrega(dto.getDataEntrega());
        if (dto.getCriadorNome() != null) {
            tarefa.setCriadorNome(dto.getCriadorNome());
        }
        if (dto.getResponsaveis() != null) {
            tarefa.setResponsaveis(new ArrayList<>(dto.getResponsaveis()));
        }

        // Atualiza checklist se fornecido
        if (dto.getChecklist() != null) {
            tarefa.getChecklist().clear();
            for (int i = 0; i < dto.getChecklist().size(); i++) {
                ChecklistItemDto itemDto = dto.getChecklist().get(i);
                tarefa.getChecklist().add(TarefaChecklistItem.builder()
                        .descricao(itemDto.getDescricao())
                        .concluido(Boolean.TRUE.equals(itemDto.getConcluido()))
                        .ordem(itemDto.getOrdem() != null ? itemDto.getOrdem() : i + 1)
                        .tarefa(tarefa)
                        .build());
            }
        }

        if ("CONCLUIDA".equalsIgnoreCase(dto.getStatus()) && tarefa.getDataConclusao() == null) {
            tarefa.setDataConclusao(LocalDateTime.now());
        }

        Tarefa saved = tarefaRepository.save(tarefa);
        return toDto(saved);
    }

    @Transactional
    public TarefaDto toggleChecklistItem(Long tarefaId, Long itemId) {
        Tarefa tarefa = tarefaRepository.findById(tarefaId)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada com ID: " + tarefaId));

        TarefaChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item de checklist não encontrado com ID: " + itemId));

        item.setConcluido(!Boolean.TRUE.equals(item.getConcluido()));
        checklistItemRepository.save(item);

        // Se todos os itens foram concluídos, pode marcar como Concluído
        boolean allDone = tarefa.getChecklist().stream().allMatch(i -> Boolean.TRUE.equals(i.getConcluido()));
        if (allDone && !tarefa.getChecklist().isEmpty()) {
            tarefa.setStatus("CONCLUIDA");
            tarefa.setDataConclusao(LocalDateTime.now());
        } else if ("CONCLUIDA".equals(tarefa.getStatus()) && !allDone) {
            tarefa.setStatus("EM_DESENVOLVIMENTO");
            tarefa.setDataConclusao(null);
        }

        Tarefa saved = tarefaRepository.save(tarefa);
        return toDto(saved);
    }

    @Transactional
    public TarefaDto atualizarStatus(Long id, String novoStatus) {
        Tarefa tarefa = tarefaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada com ID: " + id));

        tarefa.setStatus(novoStatus);
        if ("CONCLUIDA".equalsIgnoreCase(novoStatus)) {
            tarefa.setDataConclusao(LocalDateTime.now());
            // Marca todos do checklist como concluídos se finalizar
            if (tarefa.getChecklist() != null) {
                tarefa.getChecklist().forEach(item -> item.setConcluido(true));
            }
        } else {
            tarefa.setDataConclusao(null);
        }

        Tarefa saved = tarefaRepository.save(tarefa);
        return toDto(saved);
    }

    @Transactional
    public void deletar(Long id) {
        tarefaRepository.deleteById(id);
    }

    public TarefaDto toDto(Tarefa tarefa) {
        List<ChecklistItemDto> checklistDtos = new ArrayList<>();
        if (tarefa.getChecklist() != null) {
            checklistDtos = tarefa.getChecklist().stream().map(item -> ChecklistItemDto.builder()
                    .id(item.getId())
                    .descricao(item.getDescricao())
                    .concluido(item.getConcluido())
                    .ordem(item.getOrdem())
                    .build()).collect(Collectors.toList());
        }

        return TarefaDto.builder()
                .id(tarefa.getId())
                .titulo(tarefa.getTitulo())
                .descricao(tarefa.getDescricao())
                .briefing(tarefa.getBriefing())
                .loja(tarefa.getLoja())
                .clienteId(tarefa.getClienteId())
                .status(tarefa.getStatus())
                .prioridade(tarefa.getPrioridade())
                .dataGravacao(tarefa.getDataGravacao())
                .dataEntrega(tarefa.getDataEntrega())
                .criadorNome(tarefa.getCriadorNome())
                .responsaveis(tarefa.getResponsaveis() != null ? new ArrayList<>(tarefa.getResponsaveis()) : new ArrayList<>())
                .checklist(checklistDtos)
                .percentualConcluido(tarefa.getPercentualConcluido())
                .dataCriacao(tarefa.getDataCriacao())
                .dataConclusao(tarefa.getDataConclusao())
                .build();
    }
}
