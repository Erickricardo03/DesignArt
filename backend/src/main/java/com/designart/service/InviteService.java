package com.designart.service;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditActor;
import com.designart.audit.AuditActors;
import com.designart.audit.AuditMetadata;
import com.designart.audit.AuditReason;
import com.designart.audit.AuditService;
import com.designart.audit.AuditTarget;
import com.designart.audit.ClientIpResolver;
import com.designart.dto.UserDto;
import com.designart.dto.UsuarioRequest;
import com.designart.exception.ConflictException;
import com.designart.exception.InvalidRequestException;
import com.designart.exception.ResourceNotFoundException;
import com.designart.exception.ServiceUnavailableException;
import com.designart.mail.EmailTemplates;
import com.designart.mail.MailDispatcher;
import com.designart.mail.PublicLinks;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.ratelimit.RateLimitPolicy;
import com.designart.ratelimit.RateLimitService;
import com.designart.repository.TenantRepository;
import com.designart.repository.UserRepository;
import com.designart.security.PasswordPolicy;
import com.designart.security.Role;
import com.designart.tenant.TenantContext;
import com.designart.token.ActionTokenPurpose;
import com.designart.token.ActionTokenService;
import com.designart.token.IssuedToken;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Convites de usuário. Fluxo: TENANT_ADMIN informa e-mail/role/permissões -> usuário PENDENTE
 * ({@code ativo=false}, sem senha, sem e-mail verificado) + token INVITE (72 h) -> e-mail -> o usuário
 * define a PRÓPRIA senha em {@code accept-invite} -> conta ativada e e-mail verificado.
 * O administrador NUNCA define a senha e só convida para o PRÓPRIO tenant (o tenant vem do
 * TenantContext, jamais do corpo da requisição).
 * <p>
 * Consistência token x e-mail: igual à recuperação de senha ({@link PasswordResetService}): commit
 * curto (usuário + token + auditoria) -> envio assíncrono fora da transação -> se falhar, o token é
 * revogado e auditado (o usuário permanece pendente e o administrador pode reenviar). Com e-mail
 * desabilitado o convite é recusado (503) ANTES de criar qualquer coisa.
 */
@Service
@RequiredArgsConstructor
public class InviteService {

    static final String MSG_TOKEN_INVALIDO = "Convite inválido ou expirado. Peça ao administrador para reenviá-lo.";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ActionTokenService tokens;
    private final AuditService auditService;
    private final AuditActors auditActors;
    private final MailDispatcher dispatcher;
    private final EmailTemplates templates;
    private final PublicLinks links;
    private final RateLimitService rateLimit;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;
    private final ClientIpResolver ipResolver;
    private final Clock clock;

    // ------------------------------------------------------------------ criar convite
    /** Convite do TENANT_ADMIN: o tenant vem SEMPRE do contexto autenticado. */
    public UserDto invite(UsuarioRequest req) {
        Long tenantId = TenantContext.require();
        exigirEmailHabilitado();
        AuditActor ator = auditActors.current();
        rateLimit.enforce(RateLimitPolicy.INVITE_CREATE_ACTOR, String.valueOf(ator.userId()));
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant não encontrado."));

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ConvitePreparado c = tx.execute(status -> prepararConvite(tenantId, req, ator));

        despachar(c, ator, tenant.getName());
        return UserDto.from(c.usuario());
    }

    /**
     * Cria o usuário PENDENTE (sem senha, inativo), o token INVITE e os eventos de auditoria, na
     * transação JÁ ABERTA do chamador (que também pode persistir o tenant/assinatura na mesma unidade).
     * NÃO envia e-mail: o envio é feito por {@link #despachar} DEPOIS do commit. O tenantId é decidido
     * pelo chamador (contexto autenticado ou administração global), nunca pelo corpo da requisição.
     * O papel deve pertencer a um tenant (SUPER_ADMIN é sempre recusado).
     */
    public ConvitePreparado prepararConvite(Long tenantId, UsuarioRequest req, AuditActor ator) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("prepararConvite exige transação ativa.");
        }
        String email = UserRules.emailValido(req.getEmail());
        Role role = req.getRole() != null ? req.getRole() : Role.USER;
        UserRules.exigirRoleDeTenant(role);
        String ip = ipResolver.resolveCurrent();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Já existe um usuário com este e-mail.");
        }
        User pendente = User.builder()
                .email(email)
                .password(null)              // sem senha: nunca autentica antes do aceite
                .ativo(false)                // inativo até aceitar o convite
                .nomeCompleto(req.getNomeCompleto())
                .cargo(req.getCargo())
                .role(role)
                .permissoes(UserRules.permissoesEfetivas(role, req.getPermissoes()))
                .tenantId(tenantId)
                .build();
        try {
            pendente = userRepository.saveAndFlush(pendente);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe um usuário com este e-mail.");
        }
        IssuedToken token = tokens.issue(pendente.getId(), ActionTokenPurpose.INVITE, ator.userId(), ip);
        AuditTarget alvo = AuditTarget.user(pendente);
        auditService.success(AuditAction.USER_CREATED, ator, alvo,
                AuditMetadata.builder().role(pendente.getRole()).permissions(pendente.getPermissoes())
                        .activeChange(false, false).build());
        auditService.success(AuditAction.INVITE_CREATED, ator, alvo,
                AuditMetadata.builder().role(pendente.getRole()).build());
        return new ConvitePreparado(pendente, token);
    }

    // ------------------------------------------------------------------ reenviar
    public UserDto resend(Long userId) {
        return resendFor(TenantContext.require(), userId);
    }

    /** Reenvia convite de um usuário pendente DESTE tenant (outro tenant => 404). */
    public UserDto resendFor(Long tenantId, Long userId) {
        exigirEmailHabilitado();
        AuditActor ator = auditActors.current();
        rateLimit.enforce(RateLimitPolicy.INVITE_RESEND_ACTOR, String.valueOf(ator.userId()));
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant não encontrado."));
        String ip = ipResolver.resolveCurrent();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ConvitePreparado c = tx.execute(status -> {
            // Sempre filtrado pelo tenant informado: outro tenant => 404 (nunca vaza existência).
            User pendente = userRepository.findByIdAndTenantId(userId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + userId));
            if (!pendente.isPendingInvite()) {
                throw new ConflictException("Este usuário já aceitou o convite.");
            }
            tokens.revokeActive(pendente.getId(), ActionTokenPurpose.INVITE); // só o token novo funciona
            IssuedToken token = tokens.issue(pendente.getId(), ActionTokenPurpose.INVITE, ator.userId(), ip);
            auditService.success(AuditAction.INVITE_RESENT, ator, AuditTarget.user(pendente), AuditMetadata.EMPTY);
            return new ConvitePreparado(pendente, token);
        });

        despachar(c, ator, tenant.getName());
        return UserDto.from(c.usuario());
    }

    // ------------------------------------------------------------------ aceitar
    public void accept(String rawToken, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new InvalidRequestException("A confirmação da senha não confere.");
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            var ref = tokens.peek(rawToken, ActionTokenPurpose.INVITE).orElseThrow(InviteService::tokenInvalido);
            User user = userRepository.findById(ref.userId()).orElseThrow(InviteService::tokenInvalido);
            // Elegível: convite ainda pendente, papel de tenant e tenant ATIVO.
            if (!user.isPendingInvite() || user.getRole() == null || !user.getRole().belongsToTenant() || user.getTenantId() == null) {
                throw tokenInvalido();
            }
            boolean tenantAtivo = tenantRepository.findById(user.getTenantId())
                    .map(t -> "ATIVO".equals(t.getStatus())).orElse(false);
            if (!tenantAtivo) {
                throw tokenInvalido();
            }
            passwordPolicy.validate(newPassword, user.getEmail());           // antes de consumir
            if (!tokens.consume(rawToken, ActionTokenPurpose.INVITE)) {       // consumo atômico
                throw tokenInvalido();
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setEmailVerifiedAt(LocalDateTime.now(clock));
            user.setAtivo(true);
            user.revokeSessions();
            user.setFailedLogins(0);
            user.setLockedUntil(null);
            userRepository.saveAndFlush(user);
            tokens.revokeAllActive(user.getId());
            auditService.success(AuditAction.INVITE_ACCEPTED, AuditActor.of(user), AuditTarget.user(user), AuditMetadata.EMPTY);
        });
    }

    // ------------------------------------------------------------------ internos
    /** Usuário pendente + token recém-emitido (o valor puro só vive em memória até o envio do e-mail). */
    public record ConvitePreparado(User usuario, IssuedToken token) {
    }

    private static InvalidRequestException tokenInvalido() {
        return new InvalidRequestException(MSG_TOKEN_INVALIDO);
    }

    /** Sem SMTP configurado nenhum token deve ser criado (evita convite sem e-mail): 503. */
    public void exigirEmailHabilitado() {
        if (!dispatcher.isEnabled() || !links.isConfigured()) {
            throw new ServiceUnavailableException(
                    "O envio de e-mail não está configurado; não é possível enviar convites no momento.");
        }
    }

    /** Envia o e-mail FORA de qualquer transação; se falhar, revoga o token e audita (compensação). */
    public void despachar(ConvitePreparado c, AuditActor convidante, String nomeTenant) {
        User usuario = c.usuario();
        IssuedToken token = c.token();
        String nomeConvidante = userRepository.findById(convidante.userId())
                .map(User::getNomeCompleto).filter(n -> n != null && !n.isBlank()).orElse(convidante.email());
        dispatcher.dispatch(
                templates.invite(usuario.getEmail(), usuario.getNomeCompleto(), nomeConvidante, nomeTenant,
                        links.acceptInviteUrl(token.rawToken())),
                () -> compensarFalhaDeEnvio(token.id(), usuario, convidante));
    }

    private void compensarFalhaDeEnvio(Long tokenId, User usuario, AuditActor convidante) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            tokens.revokeById(tokenId);
            auditService.success(AuditAction.INVITE_EMAIL_FAILED, convidante, AuditTarget.user(usuario),
                    AuditMetadata.builder().reason(AuditReason.EMAIL_DELIVERY_FAILED).build());
        });
    }
}
