package com.designart.dto;

import com.designart.model.VendaFoto;
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
public class DashboardStatsDto {
    // Contadores de Status das Tarefas (Página 1)
    private long aFazer;
    private long emDesenvolvimento;
    private long emRevisaoOuNaoHomologada;
    private long atrasadas;
    private long concluidas;
    private long totalTarefas;

    // Métricas Financeiras (Página 5)
    private BigDecimal ganhosNoMes;
    private BigDecimal aReceber;
    private BigDecimal atrasados;
    private long visitasNaPagina;

    // Avisos de tarefas próximas e vencidas (Página 1)
    @Builder.Default
    private List<AvisoDto> avisos = new ArrayList<>();

    // Gráfico de Produção consolidada do ano (Página 3)
    @Builder.Default
    private List<ProducaoMensalDto> producaoMensal = new ArrayList<>();

    // Ranking Top Colaboradores (Página 4)
    @Builder.Default
    private List<ColaboradorRankingDto> rankingColaboradores = new ArrayList<>();

    // Últimas Vendas (Página 5)
    @Builder.Default
    private List<VendaFoto> ultimasVendas = new ArrayList<>();
}
