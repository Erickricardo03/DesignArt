package com.designart.service;

import com.designart.dto.FluxoCaixaDto;
import com.designart.model.Despesa;
import com.designart.model.VendaFoto;
import com.designart.repository.DespesaRepository;
import com.designart.repository.VendaFotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DespesaService {

    private final DespesaRepository despesaRepository;
    private final VendaFotoRepository vendaFotoRepository;

    @Transactional(readOnly = true)
    public List<Despesa> listarTodas() {
        return despesaRepository.findAllByOrderByDataDespesaDesc();
    }

    @Transactional(readOnly = true)
    public Despesa buscarPorId(Long id) {
        return despesaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Despesa não encontrada com ID: " + id));
    }

    @Transactional
    public Despesa criar(Despesa despesa) {
        if (despesa.getDataRegistro() == null) {
            despesa.setDataRegistro(LocalDateTime.now());
        }
        if (despesa.getDataDespesa() == null) {
            despesa.setDataDespesa(LocalDate.now());
        }
        if (despesa.getStatus() == null) {
            despesa.setStatus("PAGO");
        }
        return despesaRepository.save(despesa);
    }

    @Transactional
    public Despesa atualizar(Long id, Despesa dados) {
        Despesa despesa = buscarPorId(id);
        despesa.setDescricao(dados.getDescricao());
        despesa.setCategoria(dados.getCategoria());
        despesa.setValor(dados.getValor());
        despesa.setDataDespesa(dados.getDataDespesa());
        despesa.setFormaPagamento(dados.getFormaPagamento());
        despesa.setStatus(dados.getStatus());
        despesa.setObservacoes(dados.getObservacoes());
        return despesaRepository.save(despesa);
    }

    @Transactional
    public void deletar(Long id) {
        despesaRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public FluxoCaixaDto obterFluxoCaixa() {
        List<VendaFoto> vendas = vendaFotoRepository.findAll();
        List<Despesa> despesas = despesaRepository.findAll();

        BigDecimal totalEntradas = vendas.stream()
                .filter(v -> "PAGO".equalsIgnoreCase(v.getStatus()))
                .map(VendaFoto::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSaidas = despesas.stream()
                .filter(d -> "PAGO".equalsIgnoreCase(d.getStatus()))
                .map(Despesa::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lucroLiquido = totalEntradas.subtract(totalSaidas);

        // Meses de Jan a Dez
        String[] mesesNomes = {"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};
        String[] mesesAbrev = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};

        List<FluxoCaixaDto.FluxoMesDto> comparativos = new ArrayList<>();
        // Valores demonstrativos baseados no PDF para o histórico dos meses
        BigDecimal[] entradasHist = {
                new BigDecimal("3200.00"), new BigDecimal("4500.00"), new BigDecimal("5800.00"),
                new BigDecimal("6200.00"), new BigDecimal("5900.00"), new BigDecimal("7400.00"),
                new BigDecimal("8200.00"), new BigDecimal("9100.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")
        };
        BigDecimal[] saidasHist = {
                new BigDecimal("1200.00"), new BigDecimal("1800.00"), new BigDecimal("2100.00"),
                new BigDecimal("2400.00"), new BigDecimal("2200.00"), new BigDecimal("2900.00"),
                new BigDecimal("3100.00"), new BigDecimal("3400.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")
        };

        for (int i = 0; i < 12; i++) {
            BigDecimal ent = entradasHist[i];
            BigDecimal sai = saidasHist[i];
            BigDecimal luc = ent.subtract(sai);
            comparativos.add(FluxoCaixaDto.FluxoMesDto.builder()
                    .mes(mesesNomes[i])
                    .mesAbreviado(mesesAbrev[i])
                    .entradas(ent)
                    .saidas(sai)
                    .lucro(luc)
                    .build());
        }

        List<Despesa> ultimas = despesas.stream().limit(10).toList();

        return FluxoCaixaDto.builder()
                .totalEntradas(totalEntradas)
                .totalSaidas(totalSaidas)
                .lucroLiquido(lucroLiquido)
                .comparativosMensais(comparativos)
                .ultimasDespesas(ultimas)
                .build();
    }
}
