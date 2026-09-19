package com.designart.controller;

import com.designart.model.Roteiro;
import com.designart.service.RoteiroService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roteiros")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@com.designart.security.TenantMember
public class RoteiroController {

    private final RoteiroService roteiroService;

    @GetMapping
    public ResponseEntity<List<Roteiro>> listar(@RequestParam(required = false) String loja) {
        return ResponseEntity.ok(roteiroService.listarTodos(loja));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Roteiro> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(roteiroService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Roteiro> criar(@RequestBody Roteiro roteiro) {
        return ResponseEntity.ok(roteiroService.criar(roteiro));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Roteiro> atualizar(@PathVariable Long id, @RequestBody Roteiro dados) {
        return ResponseEntity.ok(roteiroService.atualizar(id, dados));
    }

    @PatchMapping("/{id}/toggle-concluido")
    public ResponseEntity<Roteiro> toggleConcluido(@PathVariable Long id) {
        return ResponseEntity.ok(roteiroService.marcarComoConcluido(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        roteiroService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
