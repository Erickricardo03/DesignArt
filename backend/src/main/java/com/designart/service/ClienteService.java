package com.designart.service;

import com.designart.exception.ResourceNotFoundException;
import com.designart.model.Cliente;
import com.designart.repository.ClienteRepository;
import com.designart.repository.TarefaRepository;
import com.designart.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final TarefaRepository tarefaRepository;

    @Transactional(readOnly = true)
    public List<Cliente> listarTodos() {
        return clienteRepository.findAllByTenantIdOrderByNomeAsc(TenantContext.require());
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        return carregar(id);
    }

    @Transactional
    public Cliente criar(Cliente dados) {
        // Nunca reaproveita o objeto do body: id e tenantId enviados pelo
        // cliente são descartados; só os campos de negócio são copiados.
        Cliente cliente = Cliente.builder()
                .tenantId(TenantContext.require())
                .nome(dados.getNome())
                .segmento(dados.getSegmento())
                .contato(dados.getContato())
                .telefone(dados.getTelefone())
                .email(dados.getEmail())
                .logoUrl(dados.getLogoUrl())
                .dataCadastro(LocalDateTime.now())
                .build();
        return clienteRepository.save(cliente);
    }

    @Transactional
    public Cliente atualizar(Long id, Cliente dados) {
        Cliente cliente = carregar(id);
        cliente.setNome(dados.getNome());
        cliente.setSegmento(dados.getSegmento());
        cliente.setContato(dados.getContato());
        cliente.setTelefone(dados.getTelefone());
        cliente.setEmail(dados.getEmail());
        cliente.setLogoUrl(dados.getLogoUrl());
        return clienteRepository.save(cliente);
    }

    @Transactional
    public void deletar(Long id) {
        Long tenantId = TenantContext.require();
        if (!clienteRepository.existsByIdAndTenantId(id, tenantId)) {
            throw naoEncontrado(id);
        }
        // Tarefas do mesmo tenant que apontam para este cliente ficam sem cliente
        // (mantém o comportamento anterior: excluir cliente nunca bloqueava).
        tarefaRepository.desvincularCliente(tenantId, id);
        clienteRepository.deleteByIdAndTenantId(id, tenantId);
    }

    private Cliente carregar(Long id) {
        return clienteRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> naoEncontrado(id));
    }

    private ResourceNotFoundException naoEncontrado(Long id) {
        return new ResourceNotFoundException("Cliente não encontrado com ID: " + id);
    }
}
