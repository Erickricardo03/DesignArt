package com.designart.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tarefas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tarefa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant dono deste registro. NUNCA aceito diretamente do cliente/DTO —
    // sempre atribuído pelo service a partir de TenantContext.require().
    @Column(name = "tenant_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long tenantId;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(columnDefinition = "TEXT")
    private String briefing;

    @Column(nullable = false)
    private String loja; // Nome da Loja / Cliente

    private Long clienteId;

    @Column(nullable = false)
    @Builder.Default
    private String status = "A_FAZER"; // A_FAZER, EM_DESENVOLVIMENTO, EM_REVISAO, NAO_HOMOLOGADA, ATRASADA, CONCLUIDA

    @Column(nullable = false)
    @Builder.Default
    private String prioridade = "MEDIA"; // BAIXA, MEDIA, ALTA, URGENTE

    private LocalDate dataGravacao;

    private LocalDate dataEntrega;

    private String criadorNome;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tarefa_responsaveis", joinColumns = @JoinColumn(name = "tarefa_id"))
    @Column(name = "responsavel_nome")
    @Builder.Default
    private List<String> responsaveis = new ArrayList<>();

    @OneToMany(mappedBy = "tarefa", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    @Builder.Default
    private List<TarefaChecklistItem> checklist = new ArrayList<>();

    @Builder.Default
    private LocalDateTime dataCriacao = LocalDateTime.now();

    private LocalDateTime dataConclusao;

    @Transient
    public int getPercentualConcluido() {
        if (checklist == null || checklist.isEmpty()) {
            return "CONCLUIDA".equalsIgnoreCase(status) ? 100 : 0;
        }
        long concluidos = checklist.stream().filter(item -> Boolean.TRUE.equals(item.getConcluido())).count();
        return (int) Math.round(((double) concluidos / checklist.size()) * 100.0);
    }
}
