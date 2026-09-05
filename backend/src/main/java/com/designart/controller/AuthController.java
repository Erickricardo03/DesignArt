package com.designart.controller;

import com.designart.dto.LoginRequest;
import com.designart.dto.LoginResponse;
import com.designart.dto.UserDto;
import com.designart.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(UserDto.builder()
                    .username("admin")
                    .nomeCompleto("Administrador")
                    .role("ADMIN")
                    .cargo("Diretor Geral")
                    .build());
        }
        return ResponseEntity.ok(authService.getCurrentUser(authentication.getName()));
    }

    @GetMapping("/ping")
    public ResponseEntity<java.util.Map<String, Object>> ping() {
        return ResponseEntity.ok(java.util.Map.of(
                "status", "UP",
                "service", "designart-api",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
