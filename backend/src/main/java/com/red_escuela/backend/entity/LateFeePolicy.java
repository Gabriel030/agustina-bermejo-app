package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Política de mora institucional única (decisión de negocio: no configurable por
 * carrera/curso) — versionada por fila nueva. El recargo crece por cada
 * {@code diasPorPeriodo} de atraso más allá de {@code diasDeGracia}
 * ({@code porcentajeRecargoPorPeriodo} cada período). El corte de acceso nunca es
 * automático: a partir de {@code diasParaRiesgoDeCorte} el alumno se marca "en
 * riesgo", pero el corte real siempre lo confirma Administración a mano.
 */
@Getter
@Setter
@Entity
@Table(name = "late_fee_policy")
public class LateFeePolicy {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "dias_de_gracia", nullable = false)
    private Integer gracePeriodDays;

    @Column(name = "porcentaje_recargo_por_periodo", nullable = false)
    private BigDecimal surchargePercentagePerPeriod;

    @Column(name = "dias_por_periodo", nullable = false)
    private Integer periodDays;

    @Column(name = "dias_para_riesgo_de_corte", nullable = false)
    private Integer daysUntilCutoffRisk;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate validFrom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
