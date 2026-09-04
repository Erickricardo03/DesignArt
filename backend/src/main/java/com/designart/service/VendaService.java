package com.designart.service;

import com.designart.model.VendaFoto;
import com.designart.repository.VendaFotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VendaService {

    private final VendaFotoRepository vendaFotoRepository;

    @Transactional(readOnly = true)
    public List<VendaFoto> listarTodas(String status) {
        if (status != null && !status.isBlank() && !"TODOS".equalsIgnoreCase(status)) {
            return vendaFotoRepository.findByStatusOrderByDataVendaDesc(status.toUpperCase());
        }
        return vendaFotoRepository.findAllByOrderByDataVendaDesc();
    }

    @Transactional(readOnly = true)
    public List<VendaFoto> ultimasVendas() {
        return vendaFotoRepository.findTop10ByOrderByDataVendaDesc();
    }

    @Transactional(readOnly = true)
    public VendaFoto buscarPorId(Long id) {
        return vendaFotoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada com ID: " + id));
    }

    @Transactional
    public VendaFoto criar(VendaFoto venda) {
        if (venda.getCodigoVenda() == null || venda.getCodigoVenda().isBlank()) {
            venda.setCodigoVenda("#" + (System.currentTimeMillis() % 1000000000L));
        }
        if (venda.getDataVenda() == null) {
            venda.setDataVenda(LocalDateTime.now());
        }
        if (venda.getStatus() == null) {
            venda.setStatus("PAGO");
        }
        return vendaFotoRepository.save(venda);
    }

    @Transactional
    public VendaFoto atualizarStatus(Long id, String status) {
        VendaFoto venda = buscarPorId(id);
        venda.setStatus(status.toUpperCase());
        return vendaFotoRepository.save(venda);
    }

    @Transactional
    public void deletar(Long id) {
        vendaFotoRepository.deleteById(id);
    }
}
