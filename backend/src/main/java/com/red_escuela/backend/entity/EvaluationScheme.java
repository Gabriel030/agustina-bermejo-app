package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Getter
@Setter
@Entity
@Table(name = "evaluation_scheme")
@Check(constraints = "final_weight IS NULL OR (final_weight >= 0 AND final_weight <= 1)")
public class EvaluationScheme {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    // Cantidad esperada de parciales y TPs
    @Column(name = "exam_count_expected")
    private Integer examCountExpected;

    @Column(name = "tp_count_expected")
    private Integer tpCountExpected;

    // Indica si hay examen final y su ponderación [0..1]
    @Column(name = "has_final")
    private Boolean hasFinal;

    @Column(name = "final_weight")
    private Double finalWeight;

    // Método de agregación para calcular nota final
    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation_method")
    private GradeAggregationMethod aggregationMethod;

    // Umbral mínimo para quedar REGULAR tras la cursada. Piso institucional (4.0) seteado por
    // default en V19; queda nullable en Java por si a futuro se permite un override explícito
    // a "sin umbral" (el algoritmo de recálculo lo trata como guarda defensiva, no como el
    // caso esperado en producción).
    @Column(name = "min_regular_grade")
    private Double minRegularGrade;

    // Umbral mínimo para quedar APPROVED (en el final, o en el promedio si hasFinal=false).
    // Piso institucional (6.0) seteado por default en V19, mismo criterio que arriba.
    @Column(name = "min_approval_grade")
    private Double minApprovalGrade;

    @Column(name = "notes", length = 1000)
    private String notes;
}
