package com.designart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRequest {
    private String username;
    @lombok.ToString.Exclude
    private String password; // opcional na atualização (mantém a senha atual se vazio)
    private String nomeCompleto;
    private String cargo;
    private String role; // ADMIN ou COLABORADOR
    private Set<String> permissoes; // FINANCEIRO, EQUIPE, CONFIGURACOES
    private Boolean ativo;
}
