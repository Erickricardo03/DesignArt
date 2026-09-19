package com.designart.service;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditActor;
import com.designart.audit.AuditActors;
import com.designart.audit.AuditField;
import com.designart.audit.AuditMetadata;
import com.designart.audit.AuditReason;
import com.designart.audit.AuditService;
import com.designart.audit.AuditTarget;
import com.designart.dto.UserDto;
import com.designart.dto.UsuarioRequest;
import com.designart.exception.ConflictException;
import com.designart.exception.ForbiddenOperationException;
import com.designart.exception.ResourceNotFoundException;
import com.designart.model.User;
import com.designart.repository.UserRepository;
import com.designart.security.Permission;
import com.designart.security.Role;
import com.designart.tenant.TenantContext;
import com.designart.token.ActionTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Gestão de usuários DO PRÓPRIO TENANT, por um TENANT_ADMIN. O tenant vem sempre de
 * {@code TenantContext}. Regras anti-escalada: nunca cria/promove SUPER_ADMIN; nunca
 * remove/rebaixa/desativa o último TENANT_ADMIN ativo.
 * <p>
 * A criação de usuários acontece por CONVITE ({@link InviteService}); o administrador NUNCA
 * define nem altera a senha de ninguém. Toda mutação gera evento de auditoria NA MESMA transação
 * (AuditService.success é MANDATORY). O metadata contém apenas enums/booleanos.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AuditActors auditActors;
    private final ActionTokenService tokens;

    @Transactional(readOnly = true)
    public List<UserDto> listarTodos() {
        return userRepository.findAllByTenantId(TenantContext.require()).stream().map(UserDto::from).toList();
    }

    @Transactional
    public UserDto atualizar(Long id, UsuarioRequest req) {
        User user = carregar(id);
        Long tenantId = user.getTenantId();
        AuditActor ator = auditActors.current();
        boolean convitePendente = user.isPendingInvite();

        // Estado anterior, para auditar apenas o que realmente mudou.
        Role roleAnterior = user.getRole();
        boolean ativoAnterior = Boolean.TRUE.equals(user.getAtivo());
        Set<Permission> permissoesAnteriores = new HashSet<>(user.getPermissoes());
        Set<AuditField> camposAlterados = EnumSet.noneOf(AuditField.class);
        Set<AuditReason> motivosRevogacao = EnumSet.noneOf(AuditReason.class);

        if (convitePendente && Boolean.TRUE.equals(req.getAtivo())) {
            // Só o aceite do convite define a senha e ativa a conta; o administrador não "ativa" ninguém.
            throw new ConflictException("Este usuário ainda não aceitou o convite; a conta só é ativada quando ele definir a própria senha.");
        }

        if (req.getEmail() != null) {
            String email = UserRules.emailValido(req.getEmail());
            if (!email.equals(user.getEmail())) {
                if (convitePendente) {
                    throw new ConflictException("Convite pendente: para mudar o e-mail, exclua o usuário e convide novamente.");
                }
                if (userRepository.existsByEmail(email)) {
                    throw new ConflictException("Já existe um usuário com este e-mail.");
                }
                user.setEmail(email);
                camposAlterados.add(AuditField.EMAIL);
                motivosRevogacao.add(AuditReason.EMAIL_CHANGED); // mudança de identidade invalida sessões abertas
            }
        }

        Role novoRole = req.getRole() != null ? req.getRole() : user.getRole();
        UserRules.exigirRoleDeTenant(novoRole);
        boolean deixaDeSerAdminAtivo = user.getRole() == Role.TENANT_ADMIN && ativoAnterior
                && (novoRole != Role.TENANT_ADMIN || Boolean.FALSE.equals(req.getAtivo()));
        if (deixaDeSerAdminAtivo) {
            exigirOutroAdminAtivo(tenantId);
        }
        if (novoRole != user.getRole()) {
            user.setRole(novoRole);
            motivosRevogacao.add(AuditReason.ROLE_CHANGED);
        }
        if (Boolean.FALSE.equals(req.getAtivo()) && ativoAnterior) {
            motivosRevogacao.add(AuditReason.DEACTIVATED); // reativar depois não ressuscita JWT antigo
        }
        if (req.getAtivo() != null && !convitePendente) {
            user.setAtivo(req.getAtivo());
        }

        if (req.getNomeCompleto() != null && !Objects.equals(req.getNomeCompleto(), user.getNomeCompleto())) {
            user.setNomeCompleto(req.getNomeCompleto());
            camposAlterados.add(AuditField.NOME);
        }
        if (req.getCargo() != null && !Objects.equals(req.getCargo(), user.getCargo())) {
            user.setCargo(req.getCargo());
            camposAlterados.add(AuditField.CARGO);
        }
        if (req.getPermissoes() != null || novoRole == Role.TENANT_ADMIN) {
            user.setPermissoes(UserRules.permissoesEfetivas(novoRole,
                    req.getPermissoes() != null ? req.getPermissoes() : user.getPermissoes()));
        }
        if (!motivosRevogacao.isEmpty()) {
            user.revokeSessions();
        }
        if (motivosRevogacao.contains(AuditReason.DEACTIVATED) || motivosRevogacao.contains(AuditReason.EMAIL_CHANGED)) {
            tokens.revokeAllActive(user.getId()); // tokens pendentes (ex.: reset já solicitado) deixam de valer
        }
        User salvo = salvar(user);

        // ---- auditoria (mesma transação; só enums/booleanos no metadata) ----
        AuditTarget alvo = AuditTarget.user(salvo);
        if (!camposAlterados.isEmpty()) {
            auditService.success(AuditAction.USER_UPDATED, ator, alvo,
                    AuditMetadata.builder().fieldsChanged(camposAlterados).build());
        }
        if (roleAnterior != salvo.getRole()) {
            auditService.success(AuditAction.USER_ROLE_CHANGED, ator, alvo,
                    AuditMetadata.builder().roleChange(roleAnterior, salvo.getRole()).build());
        }
        Set<Permission> adicionadas = new HashSet<>(salvo.getPermissoes());
        adicionadas.removeAll(permissoesAnteriores);
        Set<Permission> removidas = new HashSet<>(permissoesAnteriores);
        removidas.removeAll(salvo.getPermissoes());
        if (!adicionadas.isEmpty() || !removidas.isEmpty()) {
            auditService.success(AuditAction.USER_PERMISSIONS_CHANGED, ator, alvo,
                    AuditMetadata.builder().permissionsAdded(adicionadas).permissionsRemoved(removidas).build());
        }
        boolean ativoAtual = Boolean.TRUE.equals(salvo.getAtivo());
        if (ativoAtual != ativoAnterior) {
            auditService.success(ativoAtual ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED, ator, alvo,
                    AuditMetadata.builder().activeChange(ativoAnterior, ativoAtual).build());
        }
        if (!motivosRevogacao.isEmpty()) {
            auditService.success(AuditAction.SESSIONS_REVOKED, ator, alvo,
                    AuditMetadata.builder().reasons(motivosRevogacao).build());
        }
        return UserDto.from(salvo);
    }

    @Transactional
    public void deletar(Long id) {
        Long tenantId = TenantContext.require();
        User user = carregar(id);
        if (user.getRole() == Role.TENANT_ADMIN && Boolean.TRUE.equals(user.getAtivo())) {
            exigirOutroAdminAtivo(tenantId);
        }
        AuditActor ator = auditActors.current();
        AuditTarget alvo = AuditTarget.user(user); // snapshot ANTES da remoção: o histórico sobrevive ao usuário
        tokens.revokeAllActive(user.getId());
        userRepository.deleteByIdAndTenantId(id, tenantId);
        auditService.success(AuditAction.USER_DELETED, ator, alvo,
                AuditMetadata.builder().role(user.getRole()).build());
    }

    private User carregar(Long id) {
        return userRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + id));
    }

    private void exigirOutroAdminAtivo(Long tenantId) {
        if (userRepository.countByTenantIdAndRoleAndAtivoTrue(tenantId, Role.TENANT_ADMIN) <= 1) {
            throw new ForbiddenOperationException(
                    "Não é possível remover, rebaixar ou desativar o único administrador ativo do sistema.");
        }
    }

    private User salvar(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Corrida na unicidade global de e-mail: o banco é a última barreira.
            throw new ConflictException("Já existe um usuário com este e-mail.");
        }
    }
}
