package com.designart.controller;

import com.designart.dto.UserDto;
import com.designart.dto.UsuarioRequest;
import com.designart.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserDto>> listar() {
        return ResponseEntity.ok(userService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<UserDto> criar(@RequestBody UsuarioRequest request) {
        return ResponseEntity.ok(userService.criar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> atualizar(@PathVariable Long id, @RequestBody UsuarioRequest request) {
        return ResponseEntity.ok(userService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        userService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
