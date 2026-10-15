package com.red_escuela.backend.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public record Grade(BigDecimal value) implements Comparable<Grade> {

    private static final BigDecimal MIN = BigDecimal.ONE;
    private static final BigDecimal MAX = BigDecimal.TEN;
    private static final int SCALE = 2;
    private static final int AVERAGE_WORKING_SCALE = 6;

    public Grade {
        Objects.requireNonNull(value, "value");
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidGradeException(value);
        }
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new InvalidGradeException(value);
        }
        value = value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Grade of(BigDecimal value) {
        return new Grade(value);
    }

    public static Grade ofNullable(BigDecimal value) {
        return value == null ? null : new Grade(value);
    }

    public static BigDecimal valueOrNull(Grade grade) {
        return grade == null ? null : grade.value();
    }

    public static Grade averageOf(BigDecimal rawAverage) {
        Objects.requireNonNull(rawAverage, "rawAverage");
        return new Grade(rawAverage.setScale(SCALE, RoundingMode.HALF_UP));
    }

    public static Optional<Grade> average(Collection<Grade> grades) {
        Objects.requireNonNull(grades, "grades");
        if (grades.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = grades.stream().map(Grade::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rawAverage = sum.divide(BigDecimal.valueOf(grades.size()), AVERAGE_WORKING_SCALE, RoundingMode.HALF_UP);
        return Optional.of(averageOf(rawAverage));
    }

    public boolean meets(Grade threshold) {
        Objects.requireNonNull(threshold, "threshold");
        return compareTo(threshold) >= 0;
    }

    @Override
    public int compareTo(Grade other) {
        return value.compareTo(other.value);
    }
}
