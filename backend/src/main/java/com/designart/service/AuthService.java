package com.designart.service;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditActor;
import com.designart.audit.AuditMetadata;
import com.designart.audit.AuditReason;
import com.designart.audit.AuditService;
import com.designart.audit.AuditTarget;
import com.designart.config.AccessPolicy;
import com.designart.config.JwtUtil;
import com.designart.dto.LoginRequest;
import com.designart.dto.LoginResponse;
import com.designart.dto.UserDto;
import com.designart.exception.InvalidCredentialsException;
import com.designart.model.User;
import com.designart.repository.UserRepository;
import com.designart.security.EmailAddress;
import com.designart.security.PasswordPolicy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    // Uma única mensagem para TODA falha de login (e-mail inexistente/malformado,
    // senha errada, usuário inativo, tenant suspenso, conta bloqueada, regra SUPER_ADMIN/tenant):
    // nada é revelado sobre o motivo.
    private static final String MENSAGEM_CREDENCIAIS_INVALIDAS = "Usuário ou senha inválidos.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final LoginAttemptService loginAttempts;
    private final Clock clock;

    // Hash descartável para gastar o mesmo custo de BCrypt quando o e-mail não
    // existe (reduz enumeração de contas por tempo de resposta).
    private String dummyHash;

    @PostConstruct
    void prepararHashDescartavel() {
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = EmailAddress.normalizeOrNull(request.getEmail());
        String senha = request.getPassword();

        Optional<User> encontrado = email == null ? Optional.empty() : userRepository.findByEmail(email);
        User user = encontrado.orElse(null);

        // A senha é validada EXCLUSIVAMENTE pelo PasswordEncoder (BCrypt). Não há
        // comparação direta com o valor armazenado: o hash gravado no banco jamais
        // funciona como senha. Senhas acima de 72 bytes nunca podem ter sido
        // criadas pela política e não são avaliadas (evita a truncagem silenciosa
        // do BCrypt tratar "72 bytes + qualquer coisa" como a mesma senha).
        // O BCrypt é SEMPRE calculado (inclusive conta inexistente ou bloqueada): tempo equivalente.
        String hashParaComparar = (user != null && user.getPassword() != null) ? user.getPassword() : dummyHash;
        boolean senhaConfere = senha != null && !PasswordPolicy.exceedsMaxBytes(senha)
                && passwordEncoder.matches(senha, hashParaComparar);

        boolean bloqueada = user != null && loginAttempts.isLocked(user);
        boolean credenciaisValidas = user != null && user.getPassword() != null && senhaConfere;

        if (!credenciaisValidas) {
            if (bloqueada) {
                registrarFalhaDeLogin(user, AuditReason.ACCOUNT_LOCKED); // não conta nova falha enquanto bloqueada
            } else {
                registrarFalhaDeLogin(user, user == null ? AuditReason.UNKNOWN_ACCOUNT : AuditReason.BAD_CREDENTIALS);
                if (user != null) {
                    // Falha de SENHA em conta existente: lockout progressivo (aplicado em transação independente).
                    loginAttempts.registerFailure(user).ifPresent(ate -> auditService.failureIndependent(
                            AuditAction.ACCOUNT_LOCKED, AuditActor.anonymous(), AuditTarget.user(user),
                            AuditMetadata.builder().reason(AuditReason.ACCOUNT_LOCKED).build()));
                }
            }
            throw new InvalidCredentialsException(MENSAGEM_CREDENCIAIS_INVALIDAS);
        }
        if (bloqueada) {
            // Senha correta, mas a conta está bloqueada: mesma resposta genérica.
            registrarFalhaDeLogin(user, AuditReason.ACCOUNT_LOCKED);
            throw new InvalidCredentialsException(MENSAGEM_CREDENCIAIS_INVALIDAS);
        }
        if (!accessPolicy.podeOperar(user)) {
            registrarFalhaDeLogin(user, AuditReason.ACCESS_DENIED);
            throw new InvalidCredentialsException(MENSAGEM_CREDENCIAIS_INVALIDAS);
        }

        user.setLastLoginAt(LocalDateTime.now(clock));
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        // Mesma transação do login: se a auditoria falhar, o login não é concedido (falha fechada).
        auditService.success(AuditAction.LOGIN_SUCCESS, AuditActor.of(user), AuditTarget.user(user), AuditMetadata.EMPTY);

        return LoginResponse.builder()
                .token(jwtUtil.generateToken(user))
                .type("Bearer")
                .user(UserDto.from(user))
                .build();
    }

    /**
     * LOGIN_FAILURE em transação INDEPENDENTE (a do login é desfeita ao lançar a
     * exceção). NUNCA grava o texto digitado no campo de e-mail (o usuário pode ter
     * digitado a senha ali): para conta desconhecida não há alvo nenhum, só o motivo
     * (categoria fechada) e o contexto técnico (IP/User-Agent saneados). Para conta
     * existente, o alvo vem do BANCO (id, tenant e e-mail da conta), não da entrada.
     */
    private void registrarFalhaDeLogin(User usuarioIdentificado, AuditReason motivo) {
        auditService.failureIndependent(
                AuditAction.LOGIN_FAILURE,
                AuditActor.anonymous(),
                usuarioIdentificado == null ? AuditTarget.none() : AuditTarget.user(usuarioIdentificado),
                AuditMetadata.builder().reason(motivo).build());
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Usuário não encontrado ou sessão inválida."));
        if (!accessPolicy.podeOperar(user)) {
            throw new InvalidCredentialsException("Usuário não encontrado ou sessão inválida.");
        }
        return UserDto.from(user);
    }
}
