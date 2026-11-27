package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AverageRoundingBeforeThresholdCharacterizationTest {

    private static Grade grade(String value) {
        return Grade.of(new BigDecimal(value));
    }

    private static GradeItem exam(int sequence, String score, double weight) {
        GradeItem item = new GradeItem();
        item.setType(GradeItemType.EXAM);
        item.setSequenceNumber(sequence);
        item.setScore(grade(score));
        item.setWeight(weight);
        return item;
    }

    private static EvaluationScheme scheme(GradeAggregationMethod method) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setAggregationMethod(method);
        scheme.setMinRegularGrade(grade("4"));
        scheme.setMinApprovalGrade(grade("7"));
        return scheme;
    }

    @Test
    void averageIsRoundedToTwoDecimalsBeforeComparingAgainstTheRegularThreshold() {
        List<GradeItem> items = List.of(exam(1, "4", 1.0), exam(2, "4", 1.0), exam(3, "3.99", 1.0));

        assertThat(new RegularityPolicy().failsRegularAverage(scheme(GradeAggregationMethod.SIMPLE_AVERAGE), items)).isFalse();
    }

    @Test
    void averageIsRoundedToTwoDecimalsBeforeComparingAgainstTheApprovalThreshold() {
        List<GradeItem> items = List.of(exam(1, "7", 1.0), exam(2, "7", 1.0), exam(3, "6.99", 1.0));

        assertThat(new PromotionPolicy().approveByAggregateGrade(scheme(GradeAggregationMethod.SIMPLE_AVERAGE), items)).isPresent();
    }

    @Test
    void weightedAverageIsRoundedToTwoDecimalsBeforeComparingAgainstTheThreshold() {
        List<GradeItem> items = List.of(exam(1, "4", 1.0), exam(2, "3.99", 1.0), exam(3, "4", 1.0));

        Grade aggregate = CourseworkGrades.aggregate(GradeAggregationMethod.WEIGHTED_AVERAGE, items).orElseThrow();

        assertThat(aggregate).isEqualTo(grade("4.00"));
        assertThat(aggregate.meets(grade("4"))).isTrue();
    }
}
