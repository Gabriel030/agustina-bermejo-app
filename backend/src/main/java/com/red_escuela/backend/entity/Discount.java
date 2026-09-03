package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Beca/descuento individual sobre un alumno puntual — se suma con cualquier
 * {@link Promotion} segmentada que también le aplique (decisión de negocio: no
 * se toma el mayor ni son excluyentes). {@code tipo} determina cómo se lee
 * {@code valor}: PORCENTAJE (% del precio vigente), MONTO_FIJO (pesos, no se
 * ajusta si el precio sube) o BECA_TOTAL (exención completa, {@code valor} sin
 * uso).
 */
@Getter
@Setter
@Entity
@Table(name = "discount")
public class Discount {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private DiscountType tipo;

    @Column(name = "valor", nullable = false)
    private BigDecimal valor;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(name = "motivo", nullable = false)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean vigenteEl(LocalDate fecha) {
        return !fecha.isBefore(vigenteDesde) && (vigenteHasta == null || !fecha.isAfter(vigenteHasta));
    }
}
