package com.designart.controller;

import com.designart.dto.FluxoCaixaDto;
import com.designart.model.Despesa;
import com.designart.service.DespesaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/despesas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DespesaController {

    private final DespesaService despesaService;

    @GetMapping
    public ResponseEntity<List<Despesa>> listar() {
        return ResponseEntity.ok(despesaService.listarTodas());
    }

    @GetMapping("/fluxo-caixa")
    public ResponseEntity<FluxoCaixaDto> fluxoCaixa() {
        return ResponseEntity.ok(despesaService.obterFluxoCaixa());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Despesa> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(despesaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Despesa> criar(@RequestBody Despesa despesa) {
        return ResponseEntity.ok(despesaService.criar(despesa));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Despesa> atualizar(@PathVariable Long id, @RequestBody Despesa dados) {
        return ResponseEntity.ok(despesaService.atualizar(id, dados));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        despesaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
