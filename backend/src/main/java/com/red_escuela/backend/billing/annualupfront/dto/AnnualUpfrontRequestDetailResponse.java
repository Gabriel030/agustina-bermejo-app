package com.red_escuela.backend.billing.annualupfront.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontQuoteLine;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestDiscountView;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestView;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

public record AnnualUpfrontRequestDetailResponse(Long id, String status, Long programEnrollmentId, Long invoiceId,
                                                 @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer lectiveYear, LocalDateTime paymentDeadline,
                                                 List<Line> lines, Money total, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "null si el pedido no lleva promoción") Discount discount,
                                                 LocalDateTime acceptedAt, String acceptedTermsVersion, boolean paymentUnderReview,
                                                 @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String discardReason,
                                                 @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDateTime decidedAt) {

    public record Line(int lineNumber, YearMonth period, Money baseAmount, Money discountAmount, Money finalAmount) {

        static Line from(AnnualUpfrontQuoteLine line) {
            return new Line(line.lineNumber(), line.period(), line.baseAmount(), line.discountAmount(), line.finalAmount());
        }
    }

    public record Discount(String name, MonetaryAdjustment calculation, Money totalSaving) {

        static Discount from(AnnualUpfrontRequestDiscountView discount) {
            return discount == null ? null : new Discount(discount.name(), discount.calculation(), discount.totalSaving());
        }
    }

    public static AnnualUpfrontRequestDetailResponse from(AnnualUpfrontRequestView view) {
        return new AnnualUpfrontRequestDetailResponse(view.id(), view.status(), view.programEnrollmentId(), view.invoiceId(), view.lectiveYear(), view.paymentDeadline(),
                view.lines().stream().map(Line::from).toList(), view.total(), Discount.from(view.discount()), view.acceptedAt(), view.acceptedTermsVersion(),
                view.paymentUnderReview(), view.discardReason(), view.decidedAt());
    }
}
