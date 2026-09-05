package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Precio vigente desde una fecha para una carrera o un curso — versionado por fila
 * nueva, nunca se pisa un precio anterior (una factura ya emitida conserva su
 * monto congelado, ver Invoice). {@code concept} distingue matrícula (pago único
 * al inscribirse, solo para carrera) de cuota mensual (recurrente, carrera o
 * curso) — para un curso, la "primera cuota" es literalmente lo que se paga al
 * inscribirse, no existe concepto de matrícula de curso.
 */
@Getter
@Setter
@Entity
@Table(name = "fee_schedule")
public class FeeSchedule {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "concept", nullable = false)
    private FeeScheduleConcept concept;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate validFrom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
