package com.designart.service;

import com.designart.dto.ChecklistItemDto;
import com.designart.dto.TarefaDto;
import com.designart.exception.ResourceNotFoundException;
import com.designart.model.Tarefa;
import com.designart.model.TarefaChecklistItem;
import com.designart.repository.ClienteRepository;
import com.designart.repository.TarefaRepository;
import com.designart.tenant.TenantContext;
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
    private final ClienteRepository clienteRepository;

    @Transactional(readOnly = true)
    public List<TarefaDto> listarTodas(String loja, String status, String prioridade) {
        Long tenantId = TenantContext.require();
        List<Tarefa> tarefas;
        if ((loja != null && !loja.isBlank()) || (status != null && !status.isBlank()) || (prioridade != null && !prioridade.isBlank())) {
            tarefas = tarefaRepository.searchTarefas(
                    tenantId,
                    (loja != null && !loja.isBlank()) ? loja : null,
                    (status != null && !status.isBlank()) ? status : null,
                    (prioridade != null && !prioridade.isBlank()) ? prioridade : null
            );
        } else {
            tarefas = tarefaRepository.findAllByTenantIdOrderByDataCriacaoDesc(tenantId);
        }
        return tarefas.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TarefaDto buscarPorId(Long id) {
        return toDto(carregar(id));
    }

    @Transactional
    public TarefaDto criar(TarefaDto dto) {
        Long tenantId = TenantContext.require();
        validarCliente(dto.getClienteId(), tenantId);

        Tarefa tarefa = Tarefa.builder()
                .tenantId(tenantId)
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
            tarefa.setChecklist(construirChecklist(tarefa, dto.getChecklist()));
        }

        return toDto(tarefaRepository.save(tarefa));
    }

    @Transactional
    public TarefaDto atualizar(Long id, TarefaDto dto) {
        Tarefa tarefa = carregar(id);
        Long tenantId = tarefa.getTenantId();

        tarefa.setTitulo(dto.getTitulo());
        tarefa.setDescricao(dto.getDescricao());
        tarefa.setBriefing(dto.getBriefing());
        tarefa.setLoja(dto.getLoja());
        if (dto.getClienteId() != null) {
            validarCliente(dto.getClienteId(), tenantId);
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

        // Atualiza checklist se fornecido (itens novos herdam o tenant da tarefa)
        if (dto.getChecklist() != null) {
            tarefa.getChecklist().clear();
            tarefa.getChecklist().addAll(construirChecklist(tarefa, dto.getChecklist()));
        }

        if ("CONCLUIDA".equalsIgnoreCase(dto.getStatus()) && tarefa.getDataConclusao() == null) {
            tarefa.setDataConclusao(LocalDateTime.now());
        }

        return toDto(tarefaRepository.save(tarefa));
    }

    @Transactional
    public TarefaDto toggleChecklistItem(Long tarefaId, Long itemId) {
        Tarefa tarefa = carregar(tarefaId);

        // O item é procurado DENTRO da tarefa (já filtrada por tenant): um
        // itemId de outra tarefa/tenant simplesmente não é encontrado.
        TarefaChecklistItem item = tarefa.getChecklist().stream()
                .filter(i -> itemId != null && itemId.equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item de checklist não encontrado com ID: " + itemId));

        item.setConcluido(!Boolean.TRUE.equals(item.getConcluido()));

        // Se todos os itens foram concluídos, pode marcar como Concluído
        boolean allDone = tarefa.getChecklist().stream().allMatch(i -> Boolean.TRUE.equals(i.getConcluido()));
        if (allDone && !tarefa.getChecklist().isEmpty()) {
            tarefa.setStatus("CONCLUIDA");
            tarefa.setDataConclusao(LocalDateTime.now());
        } else if ("CONCLUIDA".equals(tarefa.getStatus()) && !allDone) {
            tarefa.setStatus("EM_DESENVOLVIMENTO");
            tarefa.setDataConclusao(null);
        }

        return toDto(tarefaRepository.save(tarefa));
    }

    @Transactional
    public TarefaDto atualizarStatus(Long id, String novoStatus) {
        Tarefa tarefa = carregar(id);

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

        return toDto(tarefaRepository.save(tarefa));
    }

    @Transactional
    public void deletar(Long id) {
        if (tarefaRepository.deleteByIdAndTenantId(id, TenantContext.require()) == 0) {
            throw naoEncontrada(id);
        }
    }

    /** Única porta de entrada para carregar uma tarefa por ID: sempre filtrada pelo tenant atual. */
    private Tarefa carregar(Long id) {
        return tarefaRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> naoEncontrada(id));
    }

    private ResourceNotFoundException naoEncontrada(Long id) {
        return new ResourceNotFoundException("Tarefa não encontrada com ID: " + id);
    }

    /** Impede associar uma tarefa a um cliente de outro tenant (ou inexistente). */
    private void validarCliente(Long clienteId, Long tenantId) {
        if (clienteId != null && !clienteRepository.existsByIdAndTenantId(clienteId, tenantId)) {
            throw new ResourceNotFoundException("Cliente não encontrado com ID: " + clienteId);
        }
    }

    private List<TarefaChecklistItem> construirChecklist(Tarefa tarefa, List<ChecklistItemDto> itensDto) {
        List<TarefaChecklistItem> itens = new ArrayList<>();
        for (int i = 0; i < itensDto.size(); i++) {
            ChecklistItemDto itemDto = itensDto.get(i);
            itens.add(TarefaChecklistItem.builder()
                    .tenantId(tarefa.getTenantId())
                    .descricao(itemDto.getDescricao())
                    .concluido(Boolean.TRUE.equals(itemDto.getConcluido()))
                    .ordem(itemDto.getOrdem() != null ? itemDto.getOrdem() : i + 1)
                    .tarefa(tarefa)
                    .build());
        }
        return itens;
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
