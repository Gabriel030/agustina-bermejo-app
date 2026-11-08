package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.shared.domain.Money;

import org.springframework.lang.Nullable;

import java.time.LocalDate;

public record AdvanceEnrollmentFeeView(boolean available, String reasonCode, @Nullable LocalDate opensOn, @Nullable Money amount, @Nullable LocalDate dueDate,
                                       @Nullable Long invoiceId) {

    public static final String AVAILABLE_CODE = "AVAILABLE";
}
