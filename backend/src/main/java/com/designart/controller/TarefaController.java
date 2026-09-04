package com.designart.controller;

import com.designart.dto.TarefaDto;
import com.designart.service.TarefaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tarefas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TarefaController {

    private final TarefaService tarefaService;

    @GetMapping
    public ResponseEntity<List<TarefaDto>> listar(
            @RequestParam(required = false) String loja,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String prioridade) {
        return ResponseEntity.ok(tarefaService.listarTodas(loja, status, prioridade));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TarefaDto> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(tarefaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<TarefaDto> criar(@RequestBody TarefaDto dto) {
        return ResponseEntity.ok(tarefaService.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TarefaDto> atualizar(@PathVariable Long id, @RequestBody TarefaDto dto) {
        return ResponseEntity.ok(tarefaService.atualizar(id, dto));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TarefaDto> atualizarStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(tarefaService.atualizarStatus(id, status));
    }

    @PatchMapping("/{tarefaId}/checklist/{itemId}/toggle")
    public ResponseEntity<TarefaDto> toggleChecklistItem(@PathVariable Long tarefaId, @PathVariable Long itemId) {
        return ResponseEntity.ok(tarefaService.toggleChecklistItem(tarefaId, itemId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        tarefaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
