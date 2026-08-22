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

    @Column(name = "first_exam")
    private Double firstExam;

    @Column(name = "first_exam_recovery")
    private Double firstExamRecovery;

    @Column(name = "second_exam")
    private Double secondExam;

    @Column(name = "second_exam_recovery")
    private Double secondExamRecovery;

    @Column(name = "third_exam")
    private Double thirdExam;

    @Column(name = "third_exam_recovery")
    private Double thirdExamRecovery;

    @Column(name = "final_exam")
    private Double finalExam;

    @Column(name = "final_exam_recovery_first_attempt")
    private Double finalExamRecoveryFirstAttempt;

    @Column(name = "final_exam_recovery_second_attempt")
    private Double finalExamRecoverySecondAttempt;

    @Column(name = "final_exam_recovery_third_attempt")
    private Double finalExamRecoveryThirdAttempt;

    @Column(name = "general_average")
    private Double generalAverage;

    @Column(name = "notes", length = 1000)
    private String notes;
}
