package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "subject")
public class Subject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    // Identificador de 4 dígitos que la institución usa para nombrar la materia en
    // el plan de estudio y en las correlativas (ej. "Álgebra (1001)") — asignado
    // automáticamente al create la materia (ver SubjectService.createSubject), no lo
    // elige quien la da de alta.
    @Column(name = "code", nullable = false, unique = true, length = 4)
    private String code;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusType status = StatusType.ACTIVE;

    // Tope de reintentos para rendir el final de esta materia. Null = sin límite (no hay
    // endpoint de configuración de materia en esta fase; se deja disponible para cargar
    // directamente o para una fase posterior con su propio endpoint).
    @Column(name = "max_final_exam_attempts")
    private Integer maxFinalExamAttempts;
}
