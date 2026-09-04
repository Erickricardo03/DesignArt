package com.designart.controller;

import com.designart.model.LogoCliente;
import com.designart.service.LogoClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/logos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LogoClienteController {

    private final LogoClienteService logoClienteService;

    @GetMapping
    public ResponseEntity<List<LogoCliente>> listar(@RequestParam(required = false) String clienteNome) {
        return ResponseEntity.ok(logoClienteService.listarTodos(clienteNome));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LogoCliente> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(logoClienteService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<LogoCliente> criar(@RequestBody LogoCliente logo) {
        return ResponseEntity.ok(logoClienteService.salvar(logo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        logoClienteService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
