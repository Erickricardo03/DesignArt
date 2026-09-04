package com.designart.service;

import com.designart.config.JwtUtil;
import com.designart.dto.LoginRequest;
import com.designart.dto.LoginResponse;
import com.designart.dto.UserDto;
import com.designart.model.User;
import com.designart.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new RuntimeException("Usuário ou senha inválidos."));

        // Suporta tanto senha BCrypt quanto comparação direta caso necessário
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword())
                || request.getPassword().equals(user.getPassword());

        if (!passwordMatches) {
            throw new RuntimeException("Usuário ou senha inválidos.");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nomeCompleto(user.getNomeCompleto())
                .email(user.getEmail())
                .cargo(user.getCargo())
                .role(user.getRole())
                .build();

        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .user(userDto)
                .build();
    }

    public UserDto getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nomeCompleto(user.getNomeCompleto())
                .email(user.getEmail())
                .cargo(user.getCargo())
                .role(user.getRole())
                .build();
    }
}
