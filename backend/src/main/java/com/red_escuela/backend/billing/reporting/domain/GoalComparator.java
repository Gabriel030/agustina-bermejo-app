package com.red_escuela.backend.billing.reporting.domain;

import java.math.BigDecimal;

public enum GoalComparator {
    AT_LEAST,
    AT_MOST;

    public boolean isMetBy(BigDecimal actual, BigDecimal threshold) {
        return switch (this) {
            case AT_LEAST -> actual.compareTo(threshold) >= 0;
            case AT_MOST -> actual.compareTo(threshold) <= 0;
        };
    }
}
