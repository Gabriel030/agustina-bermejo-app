package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cohorte de admisión de una carrera — concepto puramente administrativo/de
 * facturación, no académico: no se integra con {@link Term}/{@link SubjectSection}
 * porque en el dominio real son independientes (qué materia cursa un alumno en
 * qué Term no depende de a qué cohorte de admisión se anotó, ver spec técnica
 * de Épica 8 Fase 2). Reemplaza el rol que antes cumplían
 * {@code Program.admissionStart/admissionEnd} — ahora una carrera tiene
 * exactamente 2 cohortes por año lectivo (política institucional), cada una
 * con su propia ventana de admisión.
 */
@Getter
@Setter
@Entity
@Table(name = "program_cohort")
public class ProgramCohort {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "label")
    private String label;

    // 1 o 2 — de cuál de las 2 cohortes del año se trata. Se guarda explícito
    // en vez de inferirse comparando meses: queda auditable y no depende de un
    // cálculo implícito frágil si algún año la institución corre las fechas.
    @Column(name = "orden_en_anio", nullable = false)
    private Integer ordenEnAnio;

    @Column(name = "admission_start")
    private LocalDateTime admissionStart;

    @Column(name = "admission_end")
    private LocalDateTime admissionEnd;

    // 1er día de cursada real de esta cohorte — ancla del plan de cuotas
    // (InstallmentScheduleCalculator la usa para saber qué cuota corresponde
    // a cada mes, nunca al mes de la solicitud/inscripción).
    @Column(name = "cursada_start", nullable = false)
    private LocalDate cursadaStart;
}
