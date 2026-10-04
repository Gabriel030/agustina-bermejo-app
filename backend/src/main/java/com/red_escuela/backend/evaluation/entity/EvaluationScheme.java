package com.red_escuela.backend.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import com.red_escuela.backend.evaluation.exception.InvalidEvaluationSchemeException;
import com.red_escuela.backend.shared.domain.Grade;

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
    @Column(name = "aggregation_method", nullable = false)
    private GradeAggregationMethod aggregationMethod = GradeAggregationMethod.SIMPLE_AVERAGE;

    @Column(name = "min_regular_grade")
    private Grade minRegularGrade;

    @Column(name = "min_approval_grade")
    private Grade minApprovalGrade;

    @Column(name = "promotion_min_grade")
    private Grade promotionMinGrade;

    @Column(name = "notes", length = 1000)
    private String notes;

    private static final int MAX_EXAM_COUNT = 4;

    public void define(String name, Integer examCountExpected, Boolean hasFinal, Double finalWeight,
                       GradeAggregationMethod aggregationMethod, Grade minRegularGrade, Grade minApprovalGrade,
                       Grade promotionMinGrade, String notes) {
        if (examCountExpected < 1 || examCountExpected > MAX_EXAM_COUNT) {
            throw new InvalidEvaluationSchemeException("la cantidad de parciales va de 1 a " + MAX_EXAM_COUNT);
        }
        if (minRegularGrade.compareTo(minApprovalGrade) > 0) {
            throw new InvalidEvaluationSchemeException("la nota para aprobar no puede ser menor que el mínimo para quedar regular");
        }
        if (promotionMinGrade != null && promotionMinGrade.compareTo(minApprovalGrade) < 0) {
            throw new InvalidEvaluationSchemeException("la nota de promoción no puede ser menor que la de aprobación ni que la de regularidad");
        }
        this.name = name;
        this.examCountExpected = examCountExpected;
        this.hasFinal = hasFinal;
        this.finalWeight = finalWeight;
        this.aggregationMethod = aggregationMethod == null ? GradeAggregationMethod.SIMPLE_AVERAGE : aggregationMethod;
        this.minRegularGrade = minRegularGrade;
        this.minApprovalGrade = minApprovalGrade;
        this.promotionMinGrade = promotionMinGrade;
        this.notes = notes;
    }
}
