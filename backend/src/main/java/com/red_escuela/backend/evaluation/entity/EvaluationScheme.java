package com.red_escuela.backend.evaluation.entity;

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

    @Column(name = "exam_count_expected")
    private Integer examCountExpected;

    @Column(name = "tp_count_expected")
    private Integer tpCountExpected;

    @Column(name = "has_final")
    private Boolean hasFinal;

    @Column(name = "final_weight")
    private Double finalWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation_method")
    private GradeAggregationMethod aggregationMethod;

    @Column(name = "min_regular_grade")
    private Double minRegularGrade;

    @Column(name = "min_approval_grade")
    private Double minApprovalGrade;

    @Column(name = "promotion_min_grade")
    private Double promotionMinGrade;

    @Column(name = "notes", length = 1000)
    private String notes;
}
