package com.designart.service;

import com.designart.dto.AvisoDto;
import com.designart.dto.ColaboradorRankingDto;
import com.designart.dto.DashboardStatsDto;
import com.designart.dto.ProducaoMensalDto;
import com.designart.model.Tarefa;
import com.designart.model.VendaFoto;
import com.designart.repository.TarefaRepository;
import com.designart.repository.VendaFotoRepository;
import com.designart.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TarefaRepository tarefaRepository;
    private final VendaFotoRepository vendaFotoRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        LocalDate hoje = LocalDate.now();
        LocalDate limiteProximas = hoje.plusDays(3);

        Long tenantId = TenantContext.require();
        List<Tarefa> todas = tarefaRepository.findAllByTenantId(tenantId);

        long aFazer = todas.stream().filter(t -> "A_FAZER".equalsIgnoreCase(t.getStatus())).count();
        long emDesenvolvimento = todas.stream().filter(t -> "EM_DESENVOLVIMENTO".equalsIgnoreCase(t.getStatus())).count();
        long emRevisaoOuNaoHomologada = todas.stream().filter(t -> 
                "EM_REVISAO".equalsIgnoreCase(t.getStatus()) || "NAO_HOMOLOGADA".equalsIgnoreCase(t.getStatus())
        ).count();
        long concluidas = todas.stream().filter(t -> "CONCLUIDA".equalsIgnoreCase(t.getStatus())).count();
        
        long atrasadas = todas.stream().filter(t -> 
                !"CONCLUIDA".equalsIgnoreCase(t.getStatus()) && 
                t.getDataEntrega() != null && 
                t.getDataEntrega().isBefore(hoje)
        ).count();

        // Avisos (Vencidas e Próximas)
        List<AvisoDto> avisos = new ArrayList<>();
        for (Tarefa t : todas) {
            if ("CONCLUIDA".equalsIgnoreCase(t.getStatus()) || t.getDataEntrega() == null) continue;

            if (t.getDataEntrega().isBefore(hoje)) {
                avisos.add(AvisoDto.builder()
                        .tarefaId(t.getId())
                        .titulo(t.getTitulo())
                        .loja(t.getLoja())
                        .tipo("VENCIDA")
                        .dataEntrega(t.getDataEntrega())
                        .prioridade(t.getPrioridade())
                        .status(t.getStatus())
                        .mensagem("Tarefa em atraso desde " + t.getDataEntrega())
                        .build());
            } else if (!t.getDataEntrega().isAfter(limiteProximas)) {
                avisos.add(AvisoDto.builder()
                        .tarefaId(t.getId())
                        .titulo(t.getTitulo())
                        .loja(t.getLoja())
                        .tipo("PROXIMA")
                        .dataEntrega(t.getDataEntrega())
                        .prioridade(t.getPrioridade())
                        .status(t.getStatus())
                        .mensagem("Prazo de entrega se encerrando em " + t.getDataEntrega())
                        .build());
            }
        }

        // Métricas financeiras
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal ganhosNoMes = vendaFotoRepository.sumGanhosNoMes(tenantId, inicioMes);
        BigDecimal aReceber = vendaFotoRepository.sumAReceber(tenantId);
        BigDecimal atrasados = vendaFotoRepository.sumAtrasados(tenantId);
        // Sem rastreamento de visitas implementado: nunca inventa números.
        long visitasNaPagina = 0L;

        // Produção Mensal (Jan-Dez)
        List<ProducaoMensalDto> producaoMensal = getProducaoMensal(todas, hoje.getYear());

        // Ranking de Colaboradores (Top 10)
        List<ColaboradorRankingDto> ranking = getRankingColaboradores(todas);

        // Últimas Vendas
        List<VendaFoto> ultimasVendas = vendaFotoRepository.findTop10ByTenantIdOrderByDataVendaDesc(tenantId);

        return DashboardStatsDto.builder()
                .aFazer(aFazer)
                .emDesenvolvimento(emDesenvolvimento)
                .emRevisaoOuNaoHomologada(emRevisaoOuNaoHomologada)
                .atrasadas(atrasadas)
                .concluidas(concluidas)
                .totalTarefas(todas.size())
                .ganhosNoMes(ganhosNoMes != null ? ganhosNoMes : BigDecimal.ZERO)
                .aReceber(aReceber != null ? aReceber : BigDecimal.ZERO)
                .atrasados(atrasados != null ? atrasados : BigDecimal.ZERO)
                .visitasNaPagina(visitasNaPagina)
                .avisos(avisos)
                .producaoMensal(producaoMensal)
                .rankingColaboradores(ranking)
                .ultimasVendas(ultimasVendas)
                .build();
    }

    private static final String[] MESES = {"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};
    private static final String[] MESES_ABREV = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
            "Jul", "Ago", "Set", "Out", "Nov", "Dez"};

    /**
     * Produção do ano calculada SOMENTE a partir das tarefas reais do tenant
     * (por mês de entrega). Sem tarefas, todos os meses retornam 0.
     */
    private List<ProducaoMensalDto> getProducaoMensal(List<Tarefa> tarefas, int ano) {
        int[] atendimentos = new int[12];
        int[] concluidos = new int[12];
        for (Tarefa t : tarefas) {
            LocalDate entrega = t.getDataEntrega();
            if (entrega == null || entrega.getYear() != ano) continue;
            int m = entrega.getMonthValue() - 1;
            atendimentos[m]++;
            if ("CONCLUIDA".equalsIgnoreCase(t.getStatus())) concluidos[m]++;
        }
        List<ProducaoMensalDto> lista = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            lista.add(ProducaoMensalDto.builder()
                    .mes(MESES[i]).mesAbreviado(MESES_ABREV[i])
                    .atendimentos(atendimentos[i]).concluidos(concluidos[i])
                    .build());
        }
        return lista;
    }

    /** Top 10 por tarefas concluídas, contando os responsáveis reais das tarefas do tenant. Sem dados: lista vazia. */
    private List<ColaboradorRankingDto> getRankingColaboradores(List<Tarefa> tarefas) {
        Map<String, Long> porColaborador = new HashMap<>();
        for (Tarefa t : tarefas) {
            if (!"CONCLUIDA".equalsIgnoreCase(t.getStatus()) || t.getResponsaveis() == null) continue;
            for (String nome : t.getResponsaveis()) {
                if (nome != null && !nome.isBlank()) porColaborador.merge(nome.trim(), 1L, Long::sum);
            }
        }
        return porColaborador.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(e -> new ColaboradorRankingDto(e.getKey(), e.getValue(), null, null))
                .toList();
    }
}
