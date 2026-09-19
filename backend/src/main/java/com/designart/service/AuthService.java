package com.designart.service;

import com.designart.config.AccessPolicy;
import com.designart.config.JwtUtil;
import com.designart.dto.LoginRequest;
import com.designart.dto.LoginResponse;
import com.designart.dto.UserDto;
import com.designart.exception.InvalidCredentialsException;
import com.designart.model.User;
import com.designart.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String MENSAGEM_CREDENCIAIS_INVALIDAS = "Usuário ou senha inválidos.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AccessPolicy accessPolicy;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new InvalidCredentialsException(MENSAGEM_CREDENCIAIS_INVALIDAS));

        // A senha é validada EXCLUSIVAMENTE pelo PasswordEncoder (BCrypt). Não há
        // nenhuma comparação direta com o valor armazenado: o hash gravado no
        // banco jamais funciona como senha, e valores legados fora do formato
        // BCrypt simplesmente não autenticam.
        boolean passwordMatches = request.getPassword() != null && user.getPassword() != null
                && passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!passwordMatches) {
            // Mesma exceção e mesma mensagem do "usuário não encontrado" acima:
            // de propósito, para não revelar se o usuário existe.
            throw new InvalidCredentialsException(MENSAGEM_CREDENCIAIS_INVALIDAS);
        }

        // Usuário inativo ou de tenant suspenso/inativo não abre sessão. Mesma
        // mensagem genérica das outras falhas: nada é revelado sobre o motivo.
        if (!accessPolicy.podeOperar(user)) {
            throw new InvalidCredentialsException(MENSAGEM_CREDENCIAIS_INVALIDAS);
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nomeCompleto(user.getNomeCompleto())
                .email(user.getEmail())
                .cargo(user.getCargo())
                .role(user.getRole())
                .permissoes(user.getPermissoes())
                .ativo(user.getAtivo())
                .build();

        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .user(userDto)
                .build();
    }

    public UserDto getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Usuário não encontrado ou sessão inválida."));
        if (!accessPolicy.podeOperar(user)) {
            throw new InvalidCredentialsException("Usuário não encontrado ou sessão inválida.");
        }

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nomeCompleto(user.getNomeCompleto())
                .email(user.getEmail())
                .cargo(user.getCargo())
                .role(user.getRole())
                .permissoes(user.getPermissoes())
                .ativo(user.getAtivo())
                .build();
    }
}
