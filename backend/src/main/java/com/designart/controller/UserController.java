package com.designart.controller;

import com.designart.dto.UserDto;
import com.designart.dto.UsuarioRequest;
import com.designart.security.TenantAdminOnly;
import com.designart.service.InviteService;
import com.designart.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@TenantAdminOnly
public class UserController {

    private final UserService userService;
    private final InviteService inviteService;

    @GetMapping
    public ResponseEntity<List<UserDto>> listar() {
        return ResponseEntity.ok(userService.listarTodos());
    }

    /** Cria um usuário PENDENTE e envia o convite por e-mail. O administrador não define a senha. */
    @PostMapping
    public ResponseEntity<UserDto> convidar(@RequestBody UsuarioRequest request) {
        return ResponseEntity.ok(inviteService.invite(request));
    }

    /** Reenvia o convite de um usuário pendente do PRÓPRIO tenant (revoga o token anterior). */
    @PostMapping("/{id}/reenviar-convite")
    public ResponseEntity<UserDto> reenviarConvite(@PathVariable Long id) {
        return ResponseEntity.ok(inviteService.resend(id));
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
