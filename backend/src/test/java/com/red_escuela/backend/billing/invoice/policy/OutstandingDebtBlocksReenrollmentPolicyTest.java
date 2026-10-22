package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-142")
@Tag("D-314")
class OutstandingDebtBlocksReenrollmentPolicyTest {

    private final OutstandingDebtBlocksReenrollmentPolicy policy = new OutstandingDebtBlocksReenrollmentPolicy();

    private PolicyVerdict verdictFor(boolean overdue, boolean withdrawnDebt, boolean inPendingRequest) {
        return policy.evaluateRule(new OutstandingDebtFacts(1L, overdue, withdrawnDebt, inPendingRequest));
    }

    @Test
    void aStudentWithoutAnyDebtMayEnroll() {
        assertThat(verdictFor(false, false, false).allowed()).isTrue();
    }

    @Test
    @Tag("D-065")
    void anOverdueInvoiceInAnyEnrollmentBlocks() {
        PolicyVerdict verdict = verdictFor(true, false, false);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("OVERDUE_INVOICE");
    }

    @Test
    void debtOfAWithdrawnEnrollmentBlocks() {
        assertThat(verdictFor(false, true, false).code()).isEqualTo("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT");
    }

    @Test
    void aPendingRequestIsReportedAsTheReasonWhenItIsPartOfTheDebt() {
        assertThat(verdictFor(true, true, true).code()).isEqualTo("DEBT_IN_PENDING_CANCELLATION_REQUEST");
    }

    @Test
    void withdrawnDebtWinsOverTheGenericOverdueReason() {
        assertThat(verdictFor(true, true, false).code()).isEqualTo("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT");
    }
}
