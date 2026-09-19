package com.designart.service;

import com.designart.exception.ResourceNotFoundException;
import com.designart.model.Evento;
import com.designart.model.FotoEvento;
import com.designart.repository.EventoRepository;
import com.designart.repository.FotoEventoRepository;
import com.designart.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final FotoEventoRepository fotoEventoRepository;

    @Transactional(readOnly = true)
    public List<Evento> listarTodos() {
        return eventoRepository.findAllByTenantIdOrderByDataEventoDesc(TenantContext.require());
    }

    @Transactional(readOnly = true)
    public Evento buscarPorId(Long id) {
        return eventoRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado com ID: " + id));
    }

    @Transactional
    public Evento criar(Evento dados) {
        Long tenantId = TenantContext.require();

        // Copia só campos de negócio: id/tenantId do body são descartados.
        Evento evento = Evento.builder()
                .tenantId(tenantId)
                .nome(dados.getNome())
                .localizacao(dados.getLocalizacao())
                .dataEvento(dados.getDataEvento())
                .horario(dados.getHorario())
                .publicoEstimado(dados.getPublicoEstimado())
                .bannerUrl(dados.getBannerUrl())
                .descricao(dados.getDescricao())
                .status(dados.getStatus() != null ? dados.getStatus() : "PUBLICADO")
                .dataCriacao(LocalDateTime.now())
                .fotos(new ArrayList<>())
                .build();
        if (dados.getPrecoFotoVendida() != null) {
            evento.setPrecoFotoVendida(dados.getPrecoFotoVendida());
        }

        // Fotos enviadas junto com o evento também são recriadas do zero no tenant atual.
        if (dados.getFotos() != null) {
            for (FotoEvento f : dados.getFotos()) {
                evento.getFotos().add(novaFoto(evento, f));
            }
        }
        return eventoRepository.save(evento);
    }

    @Transactional
    public Evento atualizar(Long id, Evento dados) {
        Evento evento = buscarPorId(id);
        evento.setNome(dados.getNome());
        evento.setLocalizacao(dados.getLocalizacao());
        evento.setDataEvento(dados.getDataEvento());
        evento.setHorario(dados.getHorario());
        evento.setPrecoFotoVendida(dados.getPrecoFotoVendida());
        evento.setPublicoEstimado(dados.getPublicoEstimado());
        evento.setBannerUrl(dados.getBannerUrl());
        evento.setDescricao(dados.getDescricao());
        evento.setStatus(dados.getStatus());
        return eventoRepository.save(evento);
    }

    @Transactional
    public FotoEvento adicionarFoto(Long eventoId, FotoEvento dados) {
        Evento evento = buscarPorId(eventoId); // 404 se o evento for de outro tenant
        FotoEvento foto = novaFoto(evento, dados);
        if (foto.getMarcaDaguaTexto() == null) {
            foto.setMarcaDaguaTexto("PROIBIDA A CIRCULAÇÃO • DESIGN ARTE");
        }
        return fotoEventoRepository.save(foto);
    }

    @Transactional
    public void deletarFoto(Long fotoId) {
        if (fotoEventoRepository.deleteByIdAndTenantId(fotoId, TenantContext.require()) == 0) {
            throw new ResourceNotFoundException("Foto não encontrada com ID: " + fotoId);
        }
    }

    @Transactional
    public void deletarEvento(Long id) {
        if (eventoRepository.deleteByIdAndTenantId(id, TenantContext.require()) == 0) {
            throw new ResourceNotFoundException("Evento não encontrado com ID: " + id);
        }
    }

    /** A foto herda SEMPRE o tenant do evento pai (nunca do body). */
    private FotoEvento novaFoto(Evento evento, FotoEvento dados) {
        FotoEvento foto = FotoEvento.builder()
                .tenantId(evento.getTenantId())
                .codigoFoto(dados.getCodigoFoto())
                .titulo(dados.getTitulo())
                .urlOuBase64(dados.getUrlOuBase64())
                .marcaDaguaTexto(dados.getMarcaDaguaTexto())
                .evento(evento)
                .dataUpload(LocalDateTime.now())
                .build();
        if (dados.getPreco() != null) {
            foto.setPreco(dados.getPreco());
        }
        if (dados.getVisualizacoes() != null) {
            foto.setVisualizacoes(dados.getVisualizacoes());
        }
        if (dados.getVendas() != null) {
            foto.setVendas(dados.getVendas());
        }
        if (foto.getMarcaDaguaTexto() == null) {
            foto.setMarcaDaguaTexto("PROIBIDA A CIRCULAÇÃO • DESIGN ARTE");
        }
        return foto;
    }
}
