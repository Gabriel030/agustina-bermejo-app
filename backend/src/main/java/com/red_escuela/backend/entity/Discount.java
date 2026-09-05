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
    private DiscountType type;

    @Column(name = "valor", nullable = false)
    private BigDecimal amount;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate validFrom;

    @Column(name = "vigente_hasta")
    private LocalDate validUntil;

    @Column(name = "motivo", nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean validOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }
}
