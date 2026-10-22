package com.red_escuela.backend.billing.note.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-378")
@Tag("D-375")
class LateFeeCorrectionCreditNoteKindTest {

    @Test
    void aCorrectionAfterAWithdrawalUsesTheCorrectionReasonAndItsOwnProcess() {
        assertThat(LateFeeCorrectionCreditNoteKind.AFTER_WITHDRAWAL.reason()).isEqualTo(CreditNoteReason.LATE_FEE_CORRECTION);
        assertThat(LateFeeCorrectionCreditNoteKind.AFTER_WITHDRAWAL.actor().processName()).isEqualTo("LateFeeWithdrawalCorrection");
    }

    @Test
    void anAnnulmentByInstallmentCancellationUsesTheAnnulmentReasonAndItsOwnProcess() {
        assertThat(LateFeeCorrectionCreditNoteKind.AFTER_INSTALLMENT_CANCELLATION.reason()).isEqualTo(CreditNoteReason.LATE_FEE_ANNULMENT);
        assertThat(LateFeeCorrectionCreditNoteKind.AFTER_INSTALLMENT_CANCELLATION.actor().processName()).isEqualTo("LateFeeInstallmentCancellationAnnulment");
    }

    @Test
    void anAnnulmentByPaymentDateUsesTheAnnulmentReasonAndItsOwnProcess() {
        assertThat(LateFeeCorrectionCreditNoteKind.AFTER_PAYMENT_DATE.reason()).isEqualTo(CreditNoteReason.LATE_FEE_ANNULMENT);
        assertThat(LateFeeCorrectionCreditNoteKind.AFTER_PAYMENT_DATE.actor().processName()).isEqualTo("LateFeePaymentDateCorrection");
    }
}
