package com.designart.service;

import com.designart.dto.FluxoCaixaDto;
import com.designart.model.Despesa;
import com.designart.model.VendaFoto;
import com.designart.repository.DespesaRepository;
import com.designart.repository.VendaFotoRepository;
import com.designart.exception.ResourceNotFoundException;
import com.designart.tenant.TenantContext;
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
        return despesaRepository.findAllByTenantIdOrderByDataDespesaDesc(TenantContext.require());
    }

    @Transactional(readOnly = true)
    public Despesa buscarPorId(Long id) {
        return despesaRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada com ID: " + id));
    }

    @Transactional
    public Despesa criar(Despesa despesa) {
        despesa.setId(null);
        despesa.setTenantId(TenantContext.require());
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
        if (despesaRepository.deleteByIdAndTenantId(id, TenantContext.require()) == 0) {
            throw new ResourceNotFoundException("Despesa não encontrada com ID: " + id);
        }
    }

    @Transactional(readOnly = true)
    public FluxoCaixaDto obterFluxoCaixa() {
        Long tenantId = TenantContext.require();
        List<VendaFoto> vendas = vendaFotoRepository.findAllByTenantId(tenantId);
        List<Despesa> despesas = despesaRepository.findAllByTenantIdOrderByDataDespesaDesc(tenantId);

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
        // Histórico mensal do ano corrente calculado SOMENTE com vendas/despesas
        // PAGAS reais do tenant. Sem movimentação, todos os meses ficam em zero.
        int ano = java.time.LocalDate.now().getYear();
        BigDecimal[] entradasHist = new BigDecimal[12];
        BigDecimal[] saidasHist = new BigDecimal[12];
        java.util.Arrays.fill(entradasHist, BigDecimal.ZERO);
        java.util.Arrays.fill(saidasHist, BigDecimal.ZERO);
        for (VendaFoto v : vendas) {
            if ("PAGO".equalsIgnoreCase(v.getStatus()) && v.getDataVenda() != null
                    && v.getDataVenda().getYear() == ano && v.getValorTotal() != null) {
                int m = v.getDataVenda().getMonthValue() - 1;
                entradasHist[m] = entradasHist[m].add(v.getValorTotal());
            }
        }
        for (Despesa d : despesas) {
            if ("PAGO".equalsIgnoreCase(d.getStatus()) && d.getDataDespesa() != null
                    && d.getDataDespesa().getYear() == ano && d.getValor() != null) {
                int m = d.getDataDespesa().getMonthValue() - 1;
                saidasHist[m] = saidasHist[m].add(d.getValor());
            }
        }

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
