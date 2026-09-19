package com.designart.controller;

import com.designart.audit.ClientIpResolver;
import com.designart.dto.AcceptInviteRequest;
import com.designart.dto.ForgotPasswordRequest;
import com.designart.dto.LoginRequest;
import com.designart.dto.LoginResponse;
import com.designart.dto.ResetPasswordRequest;
import com.designart.dto.UserDto;
import com.designart.exception.InvalidCredentialsException;
import com.designart.ratelimit.RateLimitPolicy;
import com.designart.ratelimit.RateLimitService;
import com.designart.security.AuthenticatedAny;
import com.designart.security.EmailAddress;
import com.designart.security.PublicEndpoint;
import com.designart.service.AuthService;
import com.designart.service.InviteService;
import com.designart.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    /** Resposta ÚNICA do forgot-password: idêntica exista a conta ou não (e para todos os demais casos). */
    static final String MSG_FORGOT = "Se existir uma conta elegível para este e-mail, as instruções serão enviadas.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final InviteService inviteService;
    private final RateLimitService rateLimit;
    private final ClientIpResolver ipResolver;

    // PÚBLICO (lista aprovada): login.
    @PublicEndpoint
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = String.valueOf(ipResolver.resolve(http));
        // Limites por IP e por (IP + identidade normalizada, exista ou não): nunca bloqueiam a vítima em outro IP.
        rateLimit.enforce(RateLimitPolicy.LOGIN_IP, ip);
        String identidade = EmailAddress.normalizeOrNull(request.getEmail());
        rateLimit.enforce(RateLimitPolicy.LOGIN_IP_IDENTITY, ip + "|" + (identidade == null ? "invalid" : identidade));
        return ResponseEntity.ok(authService.login(request));
    }

    // PÚBLICO: recuperação de senha. Resposta sempre genérica (202).
    @PublicEndpoint
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                              HttpServletRequest http) {
        rateLimit.enforce(RateLimitPolicy.FORGOT_IP, String.valueOf(ipResolver.resolve(http)));
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", MSG_FORGOT));
    }

    // PÚBLICO: conclui a recuperação com o token recebido por e-mail.
    @PublicEndpoint
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                             HttpServletRequest http) {
        rateLimit.enforce(RateLimitPolicy.RESET_IP, String.valueOf(ipResolver.resolve(http)));
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(Map.of("message", "Senha redefinida com sucesso. Entre com a nova senha."));
    }

    // PÚBLICO: aceite de convite (o próprio usuário define a senha).
    @PublicEndpoint
    @PostMapping("/accept-invite")
    public ResponseEntity<Map<String, String>> acceptInvite(@Valid @RequestBody AcceptInviteRequest request,
                                                            HttpServletRequest http) {
        rateLimit.enforce(RateLimitPolicy.ACCEPT_INVITE_IP, String.valueOf(ipResolver.resolve(http)));
        inviteService.accept(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(Map.of("message", "Conta ativada com sucesso. Entre com a senha que você definiu."));
    }

    @AuthenticatedAny
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        // Nunca fabrica usuário: sem autenticação real -> 401 (o SecurityConfig já
        // exige login neste endpoint; esta checagem é defesa em profundidade).
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidCredentialsException("Autenticação necessária.");
        }
        // O principal é o ID do usuário (sub do JWT), não e-mail/username.
        return ResponseEntity.ok(authService.getCurrentUser(Long.valueOf(authentication.getName())));
    }

    // PÚBLICO (lista aprovada): health-check usado pelo frontend.
    @PublicEndpoint
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "designart-api",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
