package com.designart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvisoDto {
    private Long tarefaId;
    private String titulo;
    private String loja;
    private String tipo; // VENCIDA, PROXIMA
    private LocalDate dataEntrega;
    private String prioridade;
    private String status;
    private String mensagem;
}
