package com.designart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatorioMensalItemDto {
    private Long tarefaId;
    private String tituloDemanda;
    private String loja;
    private String criadorNome;
    private List<String> participantes; // percipientes / executores
    private String status;
    private String prioridade;
    private LocalDate dataEntrega;
    private Integer percentualConcluido;
    private String mesAno;
}
