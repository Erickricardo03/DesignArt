package com.designart.service;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditActor;
import com.designart.audit.AuditMetadata;
import com.designart.audit.AuditReason;
import com.designart.audit.AuditService;
import com.designart.audit.AuditTarget;
import com.designart.audit.ClientIpResolver;
import com.designart.config.AccessPolicy;
import com.designart.exception.InvalidRequestException;
import com.designart.mail.EmailTemplates;
import com.designart.mail.MailDispatcher;
import com.designart.mail.PublicLinks;
import com.designart.model.User;
import com.designart.ratelimit.RateLimitPolicy;
import com.designart.ratelimit.RateLimitService;
import com.designart.repository.UserRepository;
import com.designart.security.EmailAddress;
import com.designart.security.PasswordPolicy;
import com.designart.token.ActionTokenPurpose;
import com.designart.token.ActionTokenService;
import com.designart.token.IssuedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Recuperação de senha por e-mail.
 *
 * <h3>Consistência token x e-mail</h3>
 * <ol>
 *   <li><b>Transação 1</b> (curta): revoga PASSWORD_RESET anteriores, emite o token (só o SHA-256 vai
 *       ao banco) e audita — commit.</li>
 *   <li><b>Fora de qualquer transação</b>: o e-mail é despachado de forma assíncrona (SMTP nunca segura
 *       transação aberta).</li>
 *   <li><b>Se o envio falhar</b> (ou a fila estiver cheia): compensação em transação própria — o token é
 *       revogado e o fato é auditado. Não sobra token utilizável sem e-mail entregue. Se o processo cair
 *       entre 1 e 2, o valor puro do token nunca saiu da memória do processo (ninguém o possui) e ele
 *       expira sozinho em 30 min.</li>
 * </ol>
 * Sem e-mail habilitado, NENHUM token é gerado (e nada é impresso).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final String MSG_TOKEN_INVALIDO = "Link inválido ou expirado. Solicite uma nova recuperação de senha.";

    private final UserRepository userRepository;
    private final AccessPolicy accessPolicy;
    private final ActionTokenService tokens;
    private final AuditService auditService;
    private final MailDispatcher dispatcher;
    private final EmailTemplates templates;
    private final PublicLinks links;
    private final RateLimitService rateLimit;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;
    private final ClientIpResolver ipResolver;
    private final Clock clock;

    /**
     * Pedido de recuperação. NUNCA revela se a conta existe: retorna sem sinalizar nada em todos os casos
     * (inexistente, inativa, tenant suspenso, pendente, limite por conta, e-mail desabilitado).
     */
    public void requestReset(String rawEmail) {
        String email = EmailAddress.normalizeOrNull(rawEmail);
        if (email == null) {
            return;
        }
        // Limite por identidade normalizada: mesma resposta (silenciosa) exista a conta ou não.
        if (!rateLimit.tryAcquire(RateLimitPolicy.FORGOT_IDENTITY, email)) {
            return;
        }
        if (!dispatcher.isEnabled() || !links.isConfigured()) {
            log.warn("Recuperação de senha solicitada com o envio de e-mail desabilitado/incompleto: nenhum token foi gerado.");
            return;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !accessPolicy.podeOperar(user)) {
            return; // inexistente, inativa, pendente de convite, tenant suspenso...: nada é gerado nem auditado
        }

        String ip = ipResolver.resolveCurrent();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        IssuedToken issued = tx.execute(status -> {
            tokens.revokeActive(user.getId(), ActionTokenPurpose.PASSWORD_RESET);
            IssuedToken novo = tokens.issue(user.getId(), ActionTokenPurpose.PASSWORD_RESET, null, ip);
            auditService.success(AuditAction.PASSWORD_RESET_REQUESTED, AuditActor.anonymous(),
                    AuditTarget.user(user), AuditMetadata.EMPTY);
            return novo;
        });

        dispatcher.dispatch(
                templates.passwordReset(user.getEmail(), user.getNomeCompleto(), links.resetPasswordUrl(issued.rawToken())),
                () -> compensarFalhaDeEnvio(issued.id(), user));
    }

    /** Conclui a recuperação: consome o token ATOMICAMENTE e troca a senha na mesma transação. */
    public void resetPassword(String rawToken, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new InvalidRequestException("A confirmação da senha não confere.");
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        User atualizado = tx.execute(status -> {
            // 1) leitura: token utilizável? (qualquer falha => mensagem genérica, sem distinguir o motivo)
            var ref = tokens.peek(rawToken, ActionTokenPurpose.PASSWORD_RESET).orElseThrow(PasswordResetService::tokenInvalido);
            User user = userRepository.findById(ref.userId()).orElseThrow(PasswordResetService::tokenInvalido);
            if (!accessPolicy.podeOperar(user)) {
                throw tokenInvalido(); // usuário inativo ou tenant suspenso desde o pedido
            }
            // 2) política de senha ANTES de consumir: senha fraca não "queima" o link
            passwordPolicy.validate(newPassword, user.getEmail());
            // 3) consumo atômico: exatamente UMA requisição vence a corrida
            if (!tokens.consume(rawToken, ActionTokenPurpose.PASSWORD_RESET)) {
                throw tokenInvalido();
            }
            // 4) efeitos (todos na mesma transação)
            user.setPassword(passwordEncoder.encode(newPassword));
            user.revokeSessions(); // JWT anteriores deixam de funcionar imediatamente
            user.setFailedLogins(0);
            user.setLockedUntil(null);
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(LocalDateTime.now(clock)); // o link chegou à caixa de entrada dele
            }
            userRepository.saveAndFlush(user);
            tokens.revokeAllActive(user.getId()); // demais tokens ativos (reset e convite) deixam de valer
            AuditTarget alvo = AuditTarget.user(user);
            auditService.success(AuditAction.PASSWORD_RESET_COMPLETED, AuditActor.of(user), alvo, AuditMetadata.EMPTY);
            auditService.success(AuditAction.SESSIONS_REVOKED, AuditActor.of(user), alvo,
                    AuditMetadata.builder().reason(AuditReason.PASSWORD_CHANGED).build());
            return user;
        });

        // Fora da transação: aviso de "senha alterada" (sem token/link). Falha só é registrada em log.
        if (dispatcher.isEnabled()) {
            dispatcher.dispatchBestEffort(templates.passwordChanged(atualizado.getEmail(), atualizado.getNomeCompleto()));
        }
    }

    private static InvalidRequestException tokenInvalido() {
        return new InvalidRequestException(MSG_TOKEN_INVALIDO);
    }

    private void compensarFalhaDeEnvio(Long tokenId, User user) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            tokens.revokeById(tokenId);
            auditService.success(AuditAction.PASSWORD_RESET_EMAIL_FAILED, AuditActor.anonymous(),
                    AuditTarget.user(user), AuditMetadata.builder().reason(AuditReason.EMAIL_DELIVERY_FAILED).build());
        });
    }
}
