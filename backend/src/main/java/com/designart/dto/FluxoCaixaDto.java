package com.designart.dto;

import com.designart.model.Despesa;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FluxoCaixaDto {
    private BigDecimal totalEntradas;
    private BigDecimal totalSaidas;
    private BigDecimal lucroLiquido;
    @Builder.Default
    private List<FluxoMesDto> comparativosMensais = new ArrayList<>();
    @Builder.Default
    private List<Despesa> ultimasDespesas = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FluxoMesDto {
        private String mes;
        private String mesAbreviado;
        private BigDecimal entradas;
        private BigDecimal saidas;
        private BigDecimal lucro;
    }
}
