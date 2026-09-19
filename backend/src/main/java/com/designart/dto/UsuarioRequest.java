package com.designart.dto;

import com.designart.security.Permission;
import com.designart.security.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Corpo de convite/atualização de usuário do tenant. Role e permissões são enums: qualquer valor
 * fora do catálogo é rejeitado na desserialização (400). NÃO há campo de senha (o administrador
 * nunca define senhas: o usuário a define ao aceitar o convite) nem de tenant (o tenant é sempre o
 * do administrador autenticado). Campos desconhecidos enviados pelo cliente são ignorados.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRequest {
    private String email;
    private String nomeCompleto;
    private String cargo;
    private Role role;
    private Set<Permission> permissoes;
    private Boolean ativo;
}
