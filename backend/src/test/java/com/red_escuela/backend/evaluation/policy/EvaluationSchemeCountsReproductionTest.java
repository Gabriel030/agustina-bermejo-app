package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationSchemeCountsReproductionTest {

    private static Grade grade(String value) {
        return Grade.of(new BigDecimal(value));
    }

    @Test
    void h2StudentWithoutAnyExamGradeDoesNotFailTheRegularAverage() {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(grade("4"));

        assertThat(new RegularityPolicy().failsRegularAverage(scheme, List.of())).isFalse();
    }
}
