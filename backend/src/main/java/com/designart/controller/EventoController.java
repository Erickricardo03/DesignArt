package com.designart.controller;

import com.designart.model.Evento;
import com.designart.model.FotoEvento;
import com.designart.service.EventoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@com.designart.security.TenantMember
public class EventoController {

    private final EventoService eventoService;

    @GetMapping
    public ResponseEntity<List<Evento>> listar() {
        return ResponseEntity.ok(eventoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Evento> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(eventoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Evento> criar(@RequestBody Evento evento) {
        return ResponseEntity.ok(eventoService.criar(evento));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Evento> atualizar(@PathVariable Long id, @RequestBody Evento dados) {
        return ResponseEntity.ok(eventoService.atualizar(id, dados));
    }

    @PostMapping("/{id}/fotos")
    public ResponseEntity<FotoEvento> adicionarFoto(@PathVariable Long id, @RequestBody FotoEvento foto) {
        return ResponseEntity.ok(eventoService.adicionarFoto(id, foto));
    }

    @DeleteMapping("/fotos/{fotoId}")
    public ResponseEntity<Void> deletarFoto(@PathVariable Long fotoId) {
        eventoService.deletarFoto(fotoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarEvento(@PathVariable Long id) {
        eventoService.deletarEvento(id);
        return ResponseEntity.noContent().build();
    }
}
