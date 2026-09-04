package com.designart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TarefaDto {
    private Long id;
    private String titulo;
    private String descricao;
    private String briefing;
    private String loja;
    private Long clienteId;
    private String status; // A_FAZER, EM_DESENVOLVIMENTO, EM_REVISAO, NAO_HOMOLOGADA, ATRASADA, CONCLUIDA
    private String prioridade; // BAIXA, MEDIA, ALTA, URGENTE
    private LocalDate dataGravacao;
    private LocalDate dataEntrega;
    private String criadorNome;
    @Builder.Default
    private List<String> responsaveis = new ArrayList<>();
    @Builder.Default
    private List<ChecklistItemDto> checklist = new ArrayList<>();
    private Integer percentualConcluido;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataConclusao;
}
