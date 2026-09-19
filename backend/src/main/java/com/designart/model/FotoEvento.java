package com.designart.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fotos_eventos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FotoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant dono deste registro, denormalizado do Evento pai: existe um
    // endpoint (DELETE /api/eventos/fotos/{fotoId}) que acessa a foto
    // diretamente por ID sem passar pelo evento — sem tenant_id próprio aqui,
    // ele não teria como ser filtrado por tenant.
    @Column(name = "tenant_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long tenantId;

    private String codigoFoto; // ex: BR26-001

    private String titulo;

    // TEXT (sem @Lob): ver LogoCliente.arquivoUrlOuBase64 — "LONGTEXT" não
    // existe no PostgreSQL.
    @Column(columnDefinition = "TEXT")
    private String urlOuBase64;

    @Builder.Default
    private BigDecimal preco = new BigDecimal("10.00");

    @Builder.Default
    private String marcaDaguaTexto = "PROIBIDA A CIRCULAÇÃO • DESIGN ARTE";

    @Builder.Default
    private Integer visualizacoes = 0;

    @Builder.Default
    private Integer vendas = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id")
    @JsonBackReference
    private Evento evento;

    @Builder.Default
    private LocalDateTime dataUpload = LocalDateTime.now();
}
