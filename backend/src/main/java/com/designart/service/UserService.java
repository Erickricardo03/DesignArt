package com.designart.service;

import com.designart.dto.UserDto;
import com.designart.dto.UsuarioRequest;
import com.designart.exception.ResourceNotFoundException;
import com.designart.model.User;
import com.designart.repository.UserRepository;
import com.designart.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DOMINIO_CORPORATIVO = "@nexusdevelopment.tech";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserDto> listarTodos() {
        return userRepository.findAllByTenantId(TenantContext.require()).stream().map(this::toDto).toList();
    }

    @Transactional
    public UserDto criar(UsuarioRequest req) {
        Long tenantId = TenantContext.require();

        String username = normalizarUsername(req.getUsername());
        if (username.isBlank()) {
            throw new RuntimeException("Informe um nome de usuário.");
        }
        // Login é feito só por username, então ele segue globalmente único
        // (entre todos os tenants) nesta fase.
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Já existe um usuário com este nome de acesso.");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new RuntimeException("Informe uma senha para o novo usuário.");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(req.getPassword()))
                .nomeCompleto(req.getNomeCompleto())
                .cargo(req.getCargo())
                .email(username + DOMINIO_CORPORATIVO)
                .role(req.getRole() != null && !req.getRole().isBlank() ? req.getRole() : "COLABORADOR")
                .permissoes(req.getPermissoes() != null ? new HashSet<>(req.getPermissoes()) : new HashSet<>())
                .ativo(req.getAtivo() != null ? req.getAtivo() : true)
                // Sempre o tenant de quem está criando — UsuarioRequest nem possui campo tenantId.
                .tenantId(tenantId)
                .build();
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto atualizar(Long id, UsuarioRequest req) {
        User user = carregar(id);

        if (req.getNomeCompleto() != null) user.setNomeCompleto(req.getNomeCompleto());
        if (req.getCargo() != null) user.setCargo(req.getCargo());
        if (req.getRole() != null && !req.getRole().isBlank()) user.setRole(req.getRole());
        if (req.getPermissoes() != null) user.setPermissoes(new HashSet<>(req.getPermissoes()));
        if (req.getAtivo() != null) user.setAtivo(req.getAtivo());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        // E-mail sempre segue a política corporativa da Nexus Development
        user.setEmail(user.getUsername() + DOMINIO_CORPORATIVO);
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void deletar(Long id) {
        Long tenantId = TenantContext.require();
        User user = carregar(id);
        // A regra "não remover o único admin" vale POR TENANT.
        if ("ADMIN".equals(user.getRole()) && userRepository.countByTenantIdAndRole(tenantId, "ADMIN") <= 1) {
            throw new RuntimeException("Não é possível remover o único administrador do sistema.");
        }
        userRepository.deleteByIdAndTenantId(id, tenantId);
    }

    private User carregar(Long id) {
        return userRepository.findByIdAndTenantId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + id));
    }

    private String normalizarUsername(String username) {
        if (username == null) return "";
        return username.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "");
    }

    private UserDto toDto(User u) {
        return UserDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .nomeCompleto(u.getNomeCompleto())
                .email(u.getEmail())
                .cargo(u.getCargo())
                .role(u.getRole())
                .permissoes(u.getPermissoes())
                .ativo(u.getAtivo())
                .build();
    }
}
