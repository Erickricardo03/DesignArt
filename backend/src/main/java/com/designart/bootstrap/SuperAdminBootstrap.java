package com.designart.bootstrap;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditActor;
import com.designart.audit.AuditMetadata;
import com.designart.audit.AuditService;
import com.designart.audit.AuditTarget;
import com.designart.model.User;
import com.designart.repository.UserRepository;
import com.designart.security.EmailAddress;
import com.designart.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Bootstrap seguro do PRIMEIRO SUPER_ADMIN.
 * <ul>
 *   <li><b>Desabilitado por padrão</b> ({@code NEXUS_BOOTSTRAP_ENABLED=false}).</li>
 *   <li>Habilitado: cria NO MÁXIMO um SUPER_ADMIN, e só se NENHUM existir (idempotente; nunca recria nem
 *       sobrescreve; um segundo SUPER_ADMIN jamais é criado por aqui).</li>
 *   <li>{@code tenant_id} é sempre NULL (invariante SUPER_ADMIN &lt;=&gt; sem tenant).</li>
 *   <li>Credencial: SOMENTE o HASH BCrypt (custo &ge; 10) em {@code NEXUS_BOOTSTRAP_PASSWORD_HASH}, gerado
 *       offline pelo operador — a senha em texto puro nunca passa pela aplicação, pelo repositório ou por
 *       logs. A política de senha (mín. 10 caracteres, máx. 72 bytes) deve ser respeitada por quem gera o
 *       hash: a aplicação não consegue verificá-la a partir de um hash.</li>
 *   <li>Habilitado com configuração inválida => a aplicação FALHA ao subir (mensagem sem valores).</li>
 *   <li>Auditoria: {@code SUPER_ADMIN_BOOTSTRAPPED} (ator SYSTEM), sem credencial.</li>
 * </ul>
 * Recomendação operacional: depois do primeiro acesso, defina {@code NEXUS_BOOTSTRAP_ENABLED=false}
 * e remova o hash do ambiente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    public enum Result {DISABLED, CREATED, ALREADY_EXISTS}

    /** BCrypt ($2a/$2b/$2y), custo 10 a 31, 53 caracteres de hash. */
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$(?:1[0-9]|2[0-9]|3[01])\\$[./A-Za-z0-9]{53}$");

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Value("${nexus.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${nexus.bootstrap.email:}")
    private String email;

    @Value("${nexus.bootstrap.password-hash:}")
    private String passwordHash;

    @Override
    public void run(ApplicationArguments args) {
        Result r = bootstrap(enabled, email, passwordHash);
        switch (r) {
            case DISABLED -> log.info("Bootstrap do SUPER_ADMIN desabilitado.");
            case CREATED -> log.warn("SUPER_ADMIN inicial criado pelo bootstrap. Desabilite NEXUS_BOOTSTRAP_ENABLED e remova o hash do ambiente.");
            case ALREADY_EXISTS -> log.info("Bootstrap do SUPER_ADMIN ignorado: já existe um SUPER_ADMIN.");
        }
    }

    /** Separado de {@link #run} para ser testável com configurações diferentes. Nunca loga credenciais. */
    public Result bootstrap(boolean enabled, String rawEmail, String hash) {
        if (!enabled) {
            return Result.DISABLED;
        }
        String normalizado = EmailAddress.normalizeOrNull(rawEmail);
        if (normalizado == null) {
            throw new IllegalStateException("NEXUS_BOOTSTRAP_ENABLED=true exige NEXUS_BOOTSTRAP_EMAIL com um e-mail válido.");
        }
        if (hash == null || !BCRYPT.matcher(hash.trim()).matches()) {
            throw new IllegalStateException(
                    "NEXUS_BOOTSTRAP_ENABLED=true exige NEXUS_BOOTSTRAP_PASSWORD_HASH com um hash BCrypt (custo >= 10) gerado offline.");
        }
        String bcrypt = hash.trim();
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
                    return Result.ALREADY_EXISTS;
                }
                if (userRepository.existsByEmail(normalizado)) {
                    throw new IllegalStateException("O e-mail do bootstrap já pertence a uma conta que não é SUPER_ADMIN; nada foi alterado.");
                }
                User admin = userRepository.saveAndFlush(User.builder()
                        .email(normalizado)
                        .password(bcrypt)
                        .nomeCompleto("Super Admin")
                        .role(Role.SUPER_ADMIN)
                        .tenantId(null)                           // invariante: SUPER_ADMIN nunca pertence a tenant
                        .ativo(true)
                        .emailVerifiedAt(LocalDateTime.now(clock))
                        .permissoes(new HashSet<>())
                        .build());
                auditService.success(AuditAction.SUPER_ADMIN_BOOTSTRAPPED, AuditActor.system(),
                        AuditTarget.user(admin), AuditMetadata.builder().role(Role.SUPER_ADMIN).build());
                return Result.CREATED;
            });
        } catch (DataIntegrityViolationException e) {
            return Result.ALREADY_EXISTS; // corrida entre instâncias: outra criou primeiro
        }
    }
}
