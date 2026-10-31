package com.red_escuela.backend.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

public record SignedPercentage(BigDecimal fraction) {

    private static final int SCALE = 6;
    private static final int POINTS_SCALE = 2;

    public SignedPercentage {
        Objects.requireNonNull(fraction, "fraction");
        fraction = fraction.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static SignedPercentage ofFraction(BigDecimal fraction) {
        return new SignedPercentage(fraction);
    }

    public static SignedPercentage ofPoints(BigDecimal points) {
        Objects.requireNonNull(points, "points");
        return new SignedPercentage(points.movePointLeft(2));
    }

    public static Optional<SignedPercentage> changeBetween(Money previous, Money current) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Money difference = current.minus(previous);
        if (previous.isZero()) {
            return Optional.empty();
        }
        return Optional.of(new SignedPercentage(difference.amount().divide(previous.amount(), SCALE, RoundingMode.HALF_UP)));
    }

    public BigDecimal points() {
        return fraction.movePointRight(2).setScale(POINTS_SCALE, RoundingMode.HALF_UP);
    }
}
