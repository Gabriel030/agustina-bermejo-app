package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ventana del mes (día de inicio a día de fin) en la que se puede emitir la
 * boleta de la cuota corriente — hoy es informativa, no bloquea a Administración
 * (decisión de negocio: queda configurada para cuando exista autoservicio de
 * generación de boleta por parte del Alumno).
 */
@Getter
@Setter
@Entity
@Table(name = "billing_period")
public class BillingPeriod {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "dia_inicio", nullable = false)
    private Integer startDay;

    @Column(name = "dia_fin", nullable = false)
    private Integer endDay;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate validFrom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
