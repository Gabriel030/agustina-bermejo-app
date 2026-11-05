package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.enrollment.events.ProgramEnrollmentGraduatedEvent;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StudentDiscountEnrollmentEndIT extends AbstractStudentDiscountIssuingIT {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth DECEMBER = YearMonth.of(2027, 12);

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private StudentDiscountStatus statusOf(StudentDiscount discount) {
        return discountRepository.findById(discount.getId()).orElseThrow().getStatus();
    }

    private void withdrawForNonpayment(Long programEnrollmentId) {
        programEnrollmentService.withdrawForNonpayment(programEnrollmentId);
    }

    private List<Map<String, Object>> historyOf(StudentDiscount discount) {
        return jdbc.queryForList("select from_status, to_status, event, actor_person_id, actor_process from student_discount_status_change where student_discount_id = ? order by id",
                discount.getId());
    }

    @Test
    void aWithdrawalEndsTheActiveDiscountsOfThatEnrollmentOnly() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, DECEMBER));
        StudentDiscount individual = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), APRIL, APRIL, "Hermano en la institución"));
        IssuingScenarioFactory.Learner other = newPlainLearner();
        StudentDiscount otherScholarship = grantAsDirectorTo(EnrollmentReference.ofProgram(other.programEnrollmentId()), scholarship("50", MARCH, DECEMBER));

        withdrawForNonpayment(learner.programEnrollmentId());

        assertThat(statusOf(scholarship)).isEqualTo(StudentDiscountStatus.ENDED);
        assertThat(statusOf(individual)).isEqualTo(StudentDiscountStatus.ENDED);
        assertThat(statusOf(otherScholarship)).isEqualTo(StudentDiscountStatus.ACTIVE);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aGraduationEndsTheActiveDiscounts() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, DECEMBER));

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                ProgramEnrollmentGraduatedEvent.graduatedOn(learner.studentId(), learner.programEnrollmentId(), LocalDate.of(2027, 3, 1))));

        assertThat(statusOf(scholarship)).isEqualTo(StudentDiscountStatus.ENDED);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aRevokedDiscountStaysRevokedOnWithdrawal() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, DECEMBER));
        revokeAsDirector(scholarship.getId(), "Ya no corresponde");

        withdrawForNonpayment(learner.programEnrollmentId());

        assertThat(statusOf(scholarship)).isEqualTo(StudentDiscountStatus.REVOKED);
        assertThat(historyOf(scholarship)).extracting(row -> row.get("event")).containsExactly("DISCOUNT_GRANTED", "DISCOUNT_REVOKED");
    }

    @Test
    void anEndedDiscountIsNotACandidateForALaterIssue() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, DECEMBER));
        withdrawForNonpayment(learner.programEnrollmentId());

        issueInstallment(MARCH);

        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, MARCH)).isEmpty();
        assertThat(discountRowsOf(scholarship.getId())).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theEndIsRecordedInTheHistoryWithAProcessActor() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, DECEMBER));

        withdrawForNonpayment(learner.programEnrollmentId());

        List<Map<String, Object>> history = historyOf(scholarship);
        assertThat(history).hasSize(2);
        Map<String, Object> ending = history.get(1);
        assertThat(ending.get("from_status")).isEqualTo("ACTIVE");
        assertThat(ending.get("to_status")).isEqualTo("ENDED");
        assertThat(ending.get("event")).isEqualTo("ENROLLMENT_ENDED");
        assertThat(ending.get("actor_process")).isEqualTo("ENROLLMENT_ENDED");
        assertThat(ending.get("actor_person_id")).isNull();
    }
}
