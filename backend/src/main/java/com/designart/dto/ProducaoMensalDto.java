package com.designart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProducaoMensalDto {
    private String mes;
    private String mesAbreviado;
    private Integer atendimentos;
    private Integer concluidos;
}
