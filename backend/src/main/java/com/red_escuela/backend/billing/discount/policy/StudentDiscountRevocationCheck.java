package com.red_escuela.backend.billing.discount.policy;

import java.time.LocalDate;

public record StudentDiscountRevocationCheck(String reason, LocalDate approvalDeadline, LocalDate today) {

    public StudentDiscountRevocationCheck(String reason) {
        this(reason, null, null);
    }
}
