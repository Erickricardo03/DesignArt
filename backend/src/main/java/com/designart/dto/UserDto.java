package com.designart.dto;

import com.designart.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Visão pública de um usuário. Nunca contém senha/hash, token_version nem tenant_id interno. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String email;
    private String nomeCompleto;
    private String cargo;
    private String role;
    private Set<String> permissoes;
    private Boolean ativo;
    /** Convite enviado e ainda não aceito (a conta não pode entrar até o aceite). */
    private boolean convitePendente;

    public static UserDto from(User u) {
        return UserDto.builder()
                .id(u.getId())
                .email(u.getEmail())
                .nomeCompleto(u.getNomeCompleto())
                .cargo(u.getCargo())
                .role(u.getRole() != null ? u.getRole().name() : null)
                .permissoes(u.getPermissoes() == null ? new TreeSet<>()
                        : u.getPermissoes().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new)))
                .ativo(u.getAtivo())
                .convitePendente(u.isPendingInvite())
                .build();
    }
}
