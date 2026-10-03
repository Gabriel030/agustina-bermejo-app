package com.red_escuela.backend.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

public record Percentage(BigDecimal fraction) {

    private static final int SCALE = 6;
    private static final int POINTS_SCALE = 2;

    public Percentage {
        Objects.requireNonNull(fraction, "fraction");
        if (fraction.signum() < 0 || fraction.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidPercentageException(fraction);
        }
        fraction = fraction.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Percentage ofFraction(BigDecimal fraction) {
        return new Percentage(fraction);
    }

    public static Percentage ofPoints(BigDecimal points) {
        Objects.requireNonNull(points, "points");
        return new Percentage(points.movePointLeft(2));
    }

    public static Optional<Percentage> ratio(long part, long total) {
        if (total == 0) {
            return Optional.empty();
        }
        BigDecimal fraction = BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP);
        return Optional.of(new Percentage(fraction));
    }

    public BigDecimal points() {
        return fraction.movePointRight(2).setScale(POINTS_SCALE, RoundingMode.HALF_UP);
    }

    public boolean exceeds(Percentage other) {
        Objects.requireNonNull(other, "other");
        return fraction.compareTo(other.fraction) > 0;
    }
}
