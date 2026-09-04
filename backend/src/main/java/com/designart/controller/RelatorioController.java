package com.designart.controller;

import com.designart.dto.RelatorioMensalItemDto;
import com.designart.service.RelatorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RelatorioController {

    private final RelatorioService relatorioService;

    @GetMapping("/mensal")
    public ResponseEntity<List<RelatorioMensalItemDto>> getRelatorioMensal(
            @RequestParam(required = false) String loja,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano) {
        return ResponseEntity.ok(relatorioService.gerarRelatorioMensal(loja, mes, ano));
    }
}
