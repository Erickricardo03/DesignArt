package com.designart.controller;

import com.designart.model.Avaliacao;
import com.designart.service.AvaliacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/avaliacoes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@com.designart.security.TenantMember
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    @GetMapping
    public ResponseEntity<List<Avaliacao>> listar(@RequestParam(required = false, defaultValue = "false") boolean apenasAtivas) {
        return ResponseEntity.ok(avaliacaoService.listarTodas(apenasAtivas));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Avaliacao> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(avaliacaoService.buscarPorId(id));
    }

    @PostMapping
    @com.designart.security.RequiresConfiguracoes
    public ResponseEntity<Avaliacao> criar(@RequestBody Avaliacao avaliacao) {
        return ResponseEntity.ok(avaliacaoService.criar(avaliacao));
    }

    @PutMapping("/{id}")
    @com.designart.security.RequiresConfiguracoes
    public ResponseEntity<Avaliacao> atualizar(@PathVariable Long id, @RequestBody Avaliacao dados) {
        return ResponseEntity.ok(avaliacaoService.atualizar(id, dados));
    }

    @DeleteMapping("/{id}")
    @com.designart.security.RequiresConfiguracoes
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        avaliacaoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
