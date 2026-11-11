package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.shared.domain.Grade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class CourseworkGrades {

    private static final int WORKING_SCALE = 6;

    private CourseworkGrades() {
    }

    static boolean allApprovedIndividually(List<GradeItem> items, Grade threshold) {
        return !items.isEmpty() && items.stream().allMatch(i -> i.getScore() != null && i.getScore().meets(threshold));
    }

    static Optional<Grade> aggregate(GradeAggregationMethod method, List<GradeItem> items) {
        List<Grade> scores = items.stream().map(GradeItem::getScore).filter(Objects::nonNull).toList();
        if (scores.isEmpty() || method == null) {
            return Optional.empty();
        }
        return switch (method) {
            case SIMPLE_AVERAGE -> Grade.average(scores);
            case WEIGHTED_AVERAGE -> {
                BigDecimal weightedSum = BigDecimal.ZERO;
                BigDecimal weightSum = BigDecimal.ZERO;
                for (GradeItem item : items) {
                    if (item.getScore() == null) {
                        continue;
                    }
                    BigDecimal weight = item.getWeight() == null ? BigDecimal.ZERO : BigDecimal.valueOf(item.getWeight());
                    weightedSum = weightedSum.add(item.getScore().value().multiply(weight));
                    weightSum = weightSum.add(weight);
                }
                yield weightSum.signum() == 0
                        ? Grade.average(scores)
                        : Optional.of(Grade.averageOf(weightedSum.divide(weightSum, WORKING_SCALE, RoundingMode.HALF_UP)));
            }
            case MAX_SCORE -> scores.stream().max(Comparator.naturalOrder());
            case LAST_ATTEMPT -> items.stream()
                    .filter(i -> i.getScore() != null)
                    .max(Comparator.comparing(GradeItem::getDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(GradeItem::getScore);
        };
    }
}
