package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

import java.time.YearMonth;

public record GrantStudentDiscountRequest(@NotNull DiscountType type, @Nullable Long discountDefinitionId, @Nullable Percentage percent, @Nullable Money amount,
                                          @Nullable InvoiceType invoiceType, @NotNull YearMonth fromPeriod, @Nullable YearMonth toPeriod, String reason) {

    public StudentDiscountGrantRequest toGrantRequest() {
        if ((percent == null) == (amount == null)) {
            throw new IllegalArgumentException("Exactly one of percent or amount is required");
        }
        MonetaryAdjustment calculation = percent != null ? new MonetaryAdjustment.Proportional(percent) : new MonetaryAdjustment.Nominal(amount);
        return new StudentDiscountGrantRequest(type, discountDefinitionId, calculation, invoiceType, fromPeriod, toPeriod, reason);
    }
}
