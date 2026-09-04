package com.designart.service;

import com.designart.dto.AvisoDto;
import com.designart.dto.ColaboradorRankingDto;
import com.designart.dto.DashboardStatsDto;
import com.designart.dto.ProducaoMensalDto;
import com.designart.model.Tarefa;
import com.designart.model.VendaFoto;
import com.designart.repository.TarefaRepository;
import com.designart.repository.UserRepository;
import com.designart.repository.VendaFotoRepository;
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
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        LocalDate hoje = LocalDate.now();
        LocalDate limiteProximas = hoje.plusDays(3);

        List<Tarefa> todas = tarefaRepository.findAll();

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
        BigDecimal ganhosNoMes = vendaFotoRepository.sumGanhosNoMes(inicioMes);
        BigDecimal aReceber = vendaFotoRepository.sumAReceber();
        BigDecimal atrasados = vendaFotoRepository.sumAtrasados();
        long visitasNaPagina = 324L; // Como no PDF (página 5)

        // Produção Mensal (Jan-Dez)
        List<ProducaoMensalDto> producaoMensal = getProducaoMensalMock();

        // Ranking de Colaboradores (Top 10)
        List<ColaboradorRankingDto> ranking = getRankingColaboradores(todas);

        // Últimas Vendas
        List<VendaFoto> ultimasVendas = vendaFotoRepository.findTop10ByOrderByDataVendaDesc();

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

    private List<ProducaoMensalDto> getProducaoMensalMock() {
        String[] meses = {"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};
        String[] abrev = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};
        int[] atendimentos = {0, 0, 3650, 3900, 3800, 3700, 3500, 0, 0, 0, 0, 0};
        int[] concluidos = {0, 0, 3500, 3750, 3680, 3600, 3420, 0, 0, 0, 0, 0};

        List<ProducaoMensalDto> lista = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            lista.add(ProducaoMensalDto.builder()
                    .mes(meses[i])
                    .mesAbreviado(abrev[i])
                    .atendimentos(atendimentos[i])
                    .concluidos(concluidos[i])
                    .build());
        }
        return lista;
    }

    private List<ColaboradorRankingDto> getRankingColaboradores(List<Tarefa> tarefas) {
        // Base com dados do PDF (Página 4)
        List<ColaboradorRankingDto> ranking = new ArrayList<>();
        ranking.add(new ColaboradorRankingDto("OSMAR ISRAEL PEREIRA MENDES", 625L, "Editor Chefe & Diretor", null));
        ranking.add(new ColaboradorRankingDto("ANTONIO AURELIO CARNEIRO DOS SANTOS", 448L, "Filmmaker & Produtor", null));
        ranking.add(new ColaboradorRankingDto("HUMBERTO NASCIMENTO DA SILVA", 364L, "Fotógrafo Sênior", null));
        ranking.add(new ColaboradorRankingDto("REBECA MAGALHÃES ARAÚJO", 290L, "Motion Designer", null));
        ranking.add(new ColaboradorRankingDto("MAIARA CALLIND SANDES", 214L, "Social Media & Roteirista", null));
        ranking.add(new ColaboradorRankingDto("SÁVILO SILVA MATTA SANTANA", 182L, "Editor de Reels", null));
        ranking.add(new ColaboradorRankingDto("ANTONIO GUSA DO NASCIMENTO F.", 164L, "Assistente de Gravação", null));
        ranking.add(new ColaboradorRankingDto("WYTHCEL CARVALHO OLIVEIRA", 126L, "Designer Gráfico", null));
        ranking.add(new ColaboradorRankingDto("WALTER BERNARDO DE SOUZA JÚNIOR", 92L, "Operador de Áudio", null));
        ranking.add(new ColaboradorRankingDto("YAINARIS CHAVEZ OCHOA", 80L, "Assistente de Produção", null));
        return ranking;
    }
}
