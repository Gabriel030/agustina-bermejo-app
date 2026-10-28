package com.red_escuela.backend.billing.note.dto;

import com.red_escuela.backend.billing.discount.domain.EnrollmentFeeDiscountFacts;
import com.red_escuela.backend.billing.discount.domain.ReleasedDiscountConsumption;
import com.red_escuela.backend.billing.note.domain.InvoiceCancellationPreview;
import com.red_escuela.backend.billing.note.domain.LiveLateFeeToAnnul;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public record InvoiceCancellationPreviewResponse(boolean allowed, List<BlockResponse> blockedBy, Money creditNoteAmount, boolean releasesChargeKey,
                                                 boolean executesImmediately, List<LateFeeToAnnulResponse> lateFeesToAnnul,
                                                 List<DiscountConsumptionResponse> discountConsumptionReleased, EnrollmentFeeResponse enrollmentFee) {

    public record BlockResponse(String code, String message) {
        static BlockResponse from(PolicyViolation violation) {
            return new BlockResponse(violation.code(), violation.message());
        }
    }

    public record LateFeeToAnnulResponse(Long debitNoteId, String invoiceNumber, Money amountToAnnul, Money paidAmount) {
        static LateFeeToAnnulResponse from(LiveLateFeeToAnnul lateFee) {
            return new LateFeeToAnnulResponse(lateFee.debitNote().getId(), lateFee.debitNote().getInvoiceNumber().value(), lateFee.amountToAnnul().value(),
                    lateFee.debitNote().getAmounts().paidAmount().value());
        }
    }

    public record DiscountConsumptionResponse(Long studentDiscountId, boolean reactivatesDiscount) {
        static DiscountConsumptionResponse from(ReleasedDiscountConsumption consumption) {
            return new DiscountConsumptionResponse(consumption.studentDiscountId(), consumption.reactivatesDiscount());
        }
    }

    public record EnrollmentFeeResponse(boolean carriesDiscount, boolean otherLiveEnrollmentFeeInSameLectiveYear, boolean reissueWouldLoseDiscount) {
        static EnrollmentFeeResponse from(EnrollmentFeeDiscountFacts facts, boolean releasesChargeKey) {
            return new EnrollmentFeeResponse(facts.carriesDiscount(), facts.otherLiveEnrollmentFeeInSameLectiveYear(), facts.reissueWouldLoseDiscount() && releasesChargeKey);
        }
    }

    public static InvoiceCancellationPreviewResponse from(InvoiceCancellationPreview preview) {
        return new InvoiceCancellationPreviewResponse(preview.allowed(), preview.blockedBy().stream().map(BlockResponse::from).toList(), preview.creditNoteAmount().value(),
                preview.releasesChargeKey(), preview.executesImmediately(), preview.lateFeesToAnnul().stream().map(LateFeeToAnnulResponse::from).toList(),
                preview.discountConsumptionReleased().stream().map(DiscountConsumptionResponse::from).toList(),
                preview.enrollmentFee().map(facts -> EnrollmentFeeResponse.from(facts, preview.releasesChargeKey())).orElse(null));
    }
}
