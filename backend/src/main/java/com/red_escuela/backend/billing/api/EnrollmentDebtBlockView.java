package com.red_escuela.backend.billing.api;

import java.util.List;

public record EnrollmentDebtBlockView(boolean blocked, List<String> violationCodes) {

    public static EnrollmentDebtBlockView clear() {
        return new EnrollmentDebtBlockView(false, List.of());
    }

    public static EnrollmentDebtBlockView blockedBy(List<String> violationCodes) {
        return new EnrollmentDebtBlockView(true, List.copyOf(violationCodes));
    }
}
