package com.designart.controller;

import com.designart.model.VendaFoto;
import com.designart.service.VendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VendaController {

    private final VendaService vendaService;

    @GetMapping
    public ResponseEntity<List<VendaFoto>> listar(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(vendaService.listarTodas(status));
    }

    @GetMapping("/ultimas")
    public ResponseEntity<List<VendaFoto>> ultimas() {
        return ResponseEntity.ok(vendaService.ultimasVendas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendaFoto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(vendaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<VendaFoto> criar(@RequestBody VendaFoto venda) {
        return ResponseEntity.ok(vendaService.criar(venda));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<VendaFoto> atualizarStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(vendaService.atualizarStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        vendaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
