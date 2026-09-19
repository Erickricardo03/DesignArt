package com.designart.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tarefa_checklist_itens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TarefaChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant dono deste item (denormalizado do pai Tarefa de propósito: existe
    // um endpoint que mexe em checklist sem recarregar o tenant da tarefa, e
    // isolamento não pode depender de sempre lembrar de seguir a FK do pai).
    @Column(name = "tenant_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long tenantId;

    @Column(nullable = false)
    private String descricao;

    @Builder.Default
    private Boolean concluido = false;

    private Integer ordem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarefa_id")
    @JsonBackReference
    private Tarefa tarefa;
}
