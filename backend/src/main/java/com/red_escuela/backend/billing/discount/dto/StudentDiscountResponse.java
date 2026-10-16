package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.time.YearMonth;

public record StudentDiscountResponse(Long id, DiscountType type, InvoiceType applicableInvoiceType, MonetaryAdjustment calculation, @Nullable Money capPerInvoice,
                                      int priority, YearMonth fromPeriod, YearMonth toPeriod, StudentDiscountStatus status, Instant grantedAt, Long grantedById,
                                      String reason, @Nullable Long authorizationRequestId, @Nullable Instant revokedAt, @Nullable String revocationReason,
                                      @Nullable YearMonth revokedFromPeriod) {

    public static StudentDiscountResponse from(StudentDiscount discount) {
        return new StudentDiscountResponse(discount.getId(), discount.getDiscountType(), discount.getApplicableInvoiceType(), discount.getCalculation(),
                discount.getCapPerInvoice() == null ? null : discount.getCapPerInvoice().value(), discount.getPriority(), discount.getFromPeriod(), discount.getToPeriod(),
                discount.getStatus(), discount.getGrantedAt(), discount.getGrantedById(), discount.getReason(), discount.getAuthorizationRequestId(), discount.getRevokedAt(),
                discount.getRevocationReason(), discount.getRevokedFromPeriod());
    }
}
