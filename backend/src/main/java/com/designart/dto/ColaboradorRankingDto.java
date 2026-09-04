package com.designart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColaboradorRankingDto {
    private String nome;
    private Long totalTarefasConcluidas;
    private String cargo;
    private String avatarUrl;
}
