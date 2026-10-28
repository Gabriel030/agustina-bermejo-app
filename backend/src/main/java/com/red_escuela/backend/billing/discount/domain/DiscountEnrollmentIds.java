package com.red_escuela.backend.billing.discount.domain;

import org.springframework.lang.Nullable;

public record DiscountEnrollmentIds(@Nullable Long programEnrollmentId, @Nullable Long courseEnrollmentId) {
}
