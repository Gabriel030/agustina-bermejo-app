package com.red_escuela.backend.billing.annualupfront.dto;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontDiscountSummary;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontEligibility;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontQuote;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontQuoteLine;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

public record AnnualUpfrontQuoteResponse(boolean eligible,
                                         @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Código del primer motivo por el que no se puede pedir el año; null si se puede") String reasonCode,
                                         @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String reasonMessage,
                                         @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Factura del pedido ya existente cuando el motivo es que ya hay un pedido") Long existingRequestInvoiceId,
                                         @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer lectiveYear,
                                         @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate requestDeadline,
                                         @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Presupuesto; solo viene si se puede pedir el año") Quote quote) {

    public record Quote(List<QuoteLine> lines, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "null si no hay promoción anual vigente") Discount discount,
                        Money total, LocalDateTime paymentDeadline) {

        static Quote from(AnnualUpfrontQuote quote) {
            return new Quote(quote.lines().stream().map(QuoteLine::from).toList(), Discount.from(quote.discount()), quote.total(), quote.paymentDeadline());
        }
    }

    public record QuoteLine(int lineNumber, YearMonth period, Money baseAmount, Money discountAmount, Money finalAmount) {

        static QuoteLine from(AnnualUpfrontQuoteLine line) {
            return new QuoteLine(line.lineNumber(), line.period(), line.baseAmount(), line.discountAmount(), line.finalAmount());
        }
    }

    public record Discount(Long definitionId, String name, MonetaryAdjustment calculation, Money savingPerLine, Money totalSaving) {

        static Discount from(AnnualUpfrontDiscountSummary summary) {
            return summary == null ? null : new Discount(summary.definitionId(), summary.name(), summary.calculation(), summary.savingPerLine(), summary.totalSaving());
        }
    }

    public static AnnualUpfrontQuoteResponse from(AnnualUpfrontEligibility eligibility) {
        return new AnnualUpfrontQuoteResponse(eligibility.eligible(), eligibility.violation() == null ? null : eligibility.violation().code(),
                eligibility.violation() == null ? null : eligibility.violation().message(), eligibility.openRequestInvoiceId(), eligibility.lectiveYear(),
                eligibility.requestDeadline(), eligibility.quote() == null ? null : Quote.from(eligibility.quote()));
    }
}
