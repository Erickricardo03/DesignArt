package com.designart.service;

import com.designart.exception.ResourceNotFoundException;
import com.designart.model.VendaFoto;
import com.designart.repository.VendaFotoRepository;
import com.designart.tenant.TenantContext;
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
        Long tenantId = TenantContext.require();
        if (status != null && !status.isBlank() && !"TODOS".equalsIgnoreCase(status)) {
            return vendaFotoRepository.findByTenantIdAndStatusOrderByDataVendaDesc(tenantId, status.toUpperCase());
        }
        return vendaFotoRepository.findAllByTenantIdOrderByDataVendaDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<VendaFoto> ultimasVendas() {
        return vendaFotoRepository.findTop10ByTenantIdOrderByDataVendaDesc(TenantContext.require());
    }

    @Transactional(readOnly = true)
    public VendaFoto buscarPorId(Long id) {
        return vendaFotoRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada com ID: " + id));
    }

    @Transactional
    public VendaFoto criar(VendaFoto venda) {
        venda.setId(null);
        venda.setTenantId(TenantContext.require());
        if (venda.getCodigoVenda() == null || venda.getCodigoVenda().isBlank()) {
            // 9 dígitos aleatórios: o código por milissegundo colidia quando duas vendas do mesmo tenant
            // eram criadas no mesmo instante (UNIQUE tenant+código => 500).
            venda.setCodigoVenda("#" + (100_000_000L + java.util.concurrent.ThreadLocalRandom.current().nextLong(900_000_000L)));
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
        if (vendaFotoRepository.deleteByIdAndTenantId(id, TenantContext.require()) == 0) {
            throw new ResourceNotFoundException("Venda não encontrada com ID: " + id);
        }
    }
}
