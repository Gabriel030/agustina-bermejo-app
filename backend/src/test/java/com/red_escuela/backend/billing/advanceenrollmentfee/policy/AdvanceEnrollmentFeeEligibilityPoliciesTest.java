package com.red_escuela.backend.billing.advanceenrollmentfee.policy;

import com.red_escuela.backend.billing.advanceenrollmentfee.AdvanceEnrollmentFeePolicySetsConfiguration;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-528")
@Tag("D-523")
@Tag("D-532")
@Tag("D-535")
@Tag("D-065")
class AdvanceEnrollmentFeeEligibilityPoliciesTest {
    private static final LocalDate OPENS = LocalDate.of(2026, 9, 1);
    private static final LocalDate CLOSES = LocalDate.of(2027, 2, 28);
    private final PolicySet<AdvanceEnrollmentFeeFacts> policySet = new AdvanceEnrollmentFeePolicySetsConfiguration().advanceEnrollmentFeeEligibilityPolicySet();

    private static AdvanceEnrollmentFeeFacts eligible(LocalDate today) {
        return new AdvanceEnrollmentFeeFacts(today, true, true, false, false, OPENS, CLOSES);
    }

    private Optional<String> codeOf(AdvanceEnrollmentFeeFacts facts) {
        return policySet.evaluateRulesUntilFirstViolation(facts).firstDenial().map(PolicyVerdict::code);
    }

    @Test
    void anActiveStudentWithoutDebtInsideTheWindowAndWithPriceCanGenerateIt() {
        assertThat(codeOf(eligible(LocalDate.of(2026, 9, 15)))).isEmpty();
        assertThat(codeOf(eligible(OPENS))).isEmpty();
        assertThat(codeOf(eligible(CLOSES))).isEmpty();
    }

    @Test
    void beforeTheOpeningDateItIsNotOpenYet() {
        assertThat(codeOf(eligible(OPENS.minusDays(1)))).contains("ADVANCE_FEE_WINDOW_NOT_OPEN");
    }

    @Test
    void afterTheWindowClosesItIsClosed() {
        assertThat(codeOf(eligible(CLOSES.plusDays(1)))).contains("ADVANCE_FEE_WINDOW_CLOSED");
    }

    @Test
    void withoutTheNextYearConfigurationNothingElseIsReported() {
        AdvanceEnrollmentFeeFacts facts = new AdvanceEnrollmentFeeFacts(LocalDate.of(2026, 8, 1), false, false, true, true, OPENS, CLOSES);

        assertThat(codeOf(facts)).contains("ADVANCE_FEE_NEXT_YEAR_NOT_CONFIGURED");
    }

    @Test
    void aStudentThatIsNotEnrolledCannotGenerateIt() {
        assertThat(codeOf(new AdvanceEnrollmentFeeFacts(LocalDate.of(2026, 9, 15), true, false, false, false, OPENS, CLOSES))).contains("ADVANCE_FEE_ENROLLMENT_NOT_ACTIVE");
    }

    @Test
    void anAlreadyGeneratedFeeIsReportedAsSuch() {
        assertThat(codeOf(new AdvanceEnrollmentFeeFacts(LocalDate.of(2026, 9, 15), true, true, true, false, OPENS, CLOSES))).contains("ADVANCE_FEE_ALREADY_ISSUED");
    }

    @Test
    void overdueDebtBlocksIt() {
        assertThat(codeOf(new AdvanceEnrollmentFeeFacts(LocalDate.of(2026, 9, 15), true, true, false, true, OPENS, CLOSES))).contains("ADVANCE_FEE_OVERDUE_DEBT");
    }

    @Test
    void theFirstViolationFollowsTheDeclaredPriority() {
        AdvanceEnrollmentFeeFacts everythingWrong = new AdvanceEnrollmentFeeFacts(OPENS.minusDays(10), true, false, true, true, OPENS, CLOSES);

        assertThat(codeOf(everythingWrong)).contains("ADVANCE_FEE_ENROLLMENT_NOT_ACTIVE");
        assertThat(codeOf(new AdvanceEnrollmentFeeFacts(OPENS.minusDays(10), true, true, true, true, OPENS, CLOSES))).contains("ADVANCE_FEE_ALREADY_ISSUED");
        assertThat(codeOf(new AdvanceEnrollmentFeeFacts(OPENS.minusDays(10), true, true, false, true, OPENS, CLOSES))).contains("ADVANCE_FEE_OVERDUE_DEBT");
    }
}
