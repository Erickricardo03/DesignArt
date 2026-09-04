package com.designart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistItemDto {
    private Long id;
    private String descricao;
    private Boolean concluido;
    private Integer ordem;
}
