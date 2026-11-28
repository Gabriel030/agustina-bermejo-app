package com.red_escuela.backend.graduation.certificate.approvedsubjects.policy;

import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsBlocker.ISSUANCE_LIMIT_REACHED;
import static com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsBlocker.NOT_REGULAR_STUDENT;
import static com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsBlocker.NO_ACTIVE_ENROLLMENT;
import static com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsBlocker.NO_APPROVED_SUBJECTS;
import static com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsBlocker.PENDING_PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedSubjectsEligibilityPolicyTest {

    private final ApprovedSubjectsEligibilityPolicy policy = new ApprovedSubjectsEligibilityPolicy();

    private ApprovedSubjectsEligibilityInput eligible() {
        return new ApprovedSubjectsEligibilityInput(BillingMode.FULL, false, true, 0, 1, 3, true);
    }

    private ApprovedSubjectsEligibilityInput with(BillingMode mode, boolean overdue, boolean regular, long issuedToday, int limit,
                                                  int approved, boolean enrollment) {
        return new ApprovedSubjectsEligibilityInput(mode, overdue, regular, issuedToday, limit, approved, enrollment);
    }

    @Test
    void everythingInOrderHasNoBlockers() {
        assertThat(policy.blockersOf(eligible())).isEmpty();
    }

    @Test
    void withoutActiveEnrollmentOnlyThatBlockerIsReported() {
        assertThat(policy.blockersOf(with(BillingMode.FULL, true, false, 5, 1, 0, false))).containsExactly(NO_ACTIVE_ENROLLMENT);
    }

    @Test
    void institutionalIrregularityBlocks() {
        assertThat(policy.blockersOf(with(BillingMode.FULL, false, false, 0, 1, 3, true))).containsExactly(NOT_REGULAR_STUDENT);
    }

    @Test
    void overdueDebtBlocksWhenBillingIsFull() {
        assertThat(policy.blockersOf(with(BillingMode.FULL, true, true, 0, 1, 3, true))).containsExactly(PENDING_PAYMENTS);
    }

    @Test
    void billingOffDoesNotCheckDebt() {
        assertThat(policy.blockersOf(with(BillingMode.OFF, true, true, 0, 1, 3, true))).isEmpty();
    }

    @Test
    void billingMirrorBlocksEvenWithoutDebtBecauseItCannotBeVerified() {
        assertThat(policy.blockersOf(with(BillingMode.MIRROR, false, true, 0, 1, 3, true))).containsExactly(PENDING_PAYMENTS);
    }

    @Test
    void billingFullWithoutDebtDoesNotBlock() {
        assertThat(policy.blockersOf(with(BillingMode.FULL, false, true, 0, 1, 3, true))).isEmpty();
    }

    @Test
    void dailyLimitIsReachedExactlyAtTheLimit() {
        assertThat(policy.blockersOf(with(BillingMode.FULL, false, true, 1, 1, 3, true))).containsExactly(ISSUANCE_LIMIT_REACHED);
        assertThat(policy.blockersOf(with(BillingMode.FULL, false, true, 2, 3, 3, true))).isEmpty();
        assertThat(policy.blockersOf(with(BillingMode.FULL, false, true, 3, 3, 3, true))).containsExactly(ISSUANCE_LIMIT_REACHED);
    }

    @Test
    void dailyLimitIsExceededWhenMoreWereIssuedThanAllowed() {
        assertThat(policy.blockersOf(with(BillingMode.FULL, false, true, 4, 3, 3, true))).containsExactly(ISSUANCE_LIMIT_REACHED);
    }

    @Test
    void noApprovedSubjectsBlocks() {
        assertThat(policy.blockersOf(with(BillingMode.FULL, false, true, 0, 1, 0, true))).containsExactly(NO_APPROVED_SUBJECTS);
    }

    @Test
    void oneApprovedSubjectIsEnough() {
        assertThat(policy.blockersOf(with(BillingMode.FULL, false, true, 0, 1, 1, true))).isEmpty();
    }

    @Test
    void severalBlockersAreReportedTogetherInFixedOrder() {
        assertThat(policy.blockersOf(with(BillingMode.FULL, true, false, 1, 1, 0, true)))
                .containsExactly(NOT_REGULAR_STUDENT, PENDING_PAYMENTS, ISSUANCE_LIMIT_REACHED, NO_APPROVED_SUBJECTS);
    }

    @Test
    void blockersOfTwoUnrelatedRulesDoNotHideEachOther() {
        assertThat(policy.blockersOf(with(BillingMode.MIRROR, false, true, 0, 1, 0, true)))
                .containsExactly(PENDING_PAYMENTS, NO_APPROVED_SUBJECTS);
    }

    @Test
    void returnedListCannotBeModified() {
        List<ApprovedSubjectsBlocker> blockers = policy.blockersOf(with(BillingMode.FULL, true, true, 0, 1, 3, true));

        assertThatThrownBy(() -> blockers.add(NOT_REGULAR_STUDENT)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullInputIsRejected() {
        assertThatThrownBy(() -> policy.blockersOf(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullBillingModeIsRejected() {
        assertThatThrownBy(() -> policy.blockersOf(with(null, false, true, 0, 1, 3, true))).isInstanceOf(NullPointerException.class);
    }
}
