package com.designart.service;

import com.designart.model.Evento;
import com.designart.model.FotoEvento;
import com.designart.repository.EventoRepository;
import com.designart.repository.FotoEventoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final FotoEventoRepository fotoEventoRepository;

    @Transactional(readOnly = true)
    public List<Evento> listarTodos() {
        return eventoRepository.findAllByOrderByDataEventoDesc();
    }

    @Transactional(readOnly = true)
    public Evento buscarPorId(Long id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evento não encontrado com ID: " + id));
    }

    @Transactional
    public Evento criar(Evento evento) {
        evento.setDataCriacao(LocalDateTime.now());
        if (evento.getStatus() == null) {
            evento.setStatus("PUBLICADO");
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
    public FotoEvento adicionarFoto(Long eventoId, FotoEvento foto) {
        Evento evento = buscarPorId(eventoId);
        foto.setEvento(evento);
        foto.setDataUpload(LocalDateTime.now());
        if (foto.getMarcaDaguaTexto() == null) {
            foto.setMarcaDaguaTexto("PROIBIDA A CIRCULAÇÃO • DESIGN ARTE");
        }
        return fotoEventoRepository.save(foto);
    }

    @Transactional
    public void deletarFoto(Long fotoId) {
        fotoEventoRepository.deleteById(fotoId);
    }

    @Transactional
    public void deletarEvento(Long id) {
        eventoRepository.deleteById(id);
    }
}
