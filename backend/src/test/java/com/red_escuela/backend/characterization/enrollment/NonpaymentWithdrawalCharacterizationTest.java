package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.shared.module.CourseEnrollmentNonpaymentWithdrawalPort;
import com.red_escuela.backend.billing.events.EnrollmentWithdrawnForBillingEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RecordApplicationEvents
class NonpaymentWithdrawalCharacterizationTest extends WithdrawalP2CharacterizationSupport {

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private CourseEnrollmentNonpaymentWithdrawalPort courseNonpaymentWithdrawalPort;
    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void programNonpaymentWithdrawalMarksEnrollmentWithdrawnAndRegistersEffectiveAndRegistrationDates() {
        Student student = persistStudent();
        ProgramEnrollment enrollment = persistProgramEnrollment(student, persistProgram(), ProgramEnrollmentStatus.ENROLLED);

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());
        entityManager.flush();
        entityManager.clear();

        ProgramEnrollment reloaded = programEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
        assertThat(reloaded.getWithdrawalEffectiveDate()).isNotNull();
        assertThat(reloaded.getWithdrawalRegisteredOn()).isNotNull();
        assertThat(reloaded.getCompletionDate()).isNotNull();
        assertThat(reloaded.getWithdrawnByPersonId()).isNull();
    }

    @Test
    void pausedProgramEnrollmentCanAlsoBeWithdrawnForNonpayment() {
        ProgramEnrollment enrollment = persistProgramEnrollment(persistStudent(), persistProgram(), ProgramEnrollmentStatus.PAUSED);

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());

        assertThat(programEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus()).isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
    }

    @Test
    void programNonpaymentWithdrawalLeavesPendingInvoicesUntouchedAndIssuesNoCreditNotes() {
        ProgramEnrollment enrollment = persistProgramEnrollment(persistStudent(), persistProgram(), ProgramEnrollmentStatus.ENROLLED);
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        Invoice later = persistPendingInstallment(enrollment, nextMonth, nextMonth.atDay(10), 80000);
        Invoice afterThat = persistPendingInstallment(enrollment, nextMonth.plusMonths(1), nextMonth.plusMonths(1).atDay(10), 80000);
        long creditNotesBefore = creditNoteCount();

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());

        assertThat(paymentStatusOf(later.getId())).isEqualTo("PENDING");
        assertThat(paymentStatusOf(afterThat.getId())).isEqualTo("PENDING");
        assertThat(creditNoteCount()).isEqualTo(creditNotesBefore);
    }

    @Test
    void programNonpaymentWithdrawalPublishesBillingEventWithNonpaymentCauseAndProgramEnrollmentId() {
        Student student = persistStudent();
        ProgramEnrollment enrollment = persistProgramEnrollment(student, persistProgram(), ProgramEnrollmentStatus.ENROLLED);

        programEnrollmentService.withdrawForNonpayment(enrollment.getId());

        List<EnrollmentWithdrawnForBillingEvent> events = applicationEvents.stream(EnrollmentWithdrawnForBillingEvent.class)
                .filter(event -> enrollment.getId().equals(event.programEnrollmentId())).toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).cause()).isEqualTo("NONPAYMENT");
        assertThat(events.get(0).studentId()).isEqualTo(student.getId());
        assertThat(events.get(0).courseEnrollmentId()).isNull();
    }

    @Test
    void withdrawingAnAlreadyWithdrawnProgramEnrollmentIsRejectedWithoutPublishingAnotherEvent() {
        ProgramEnrollment enrollment = persistProgramEnrollment(persistStudent(), persistProgram(), ProgramEnrollmentStatus.WITHDRAWN);

        assertThatThrownBy(() -> programEnrollmentService.withdrawForNonpayment(enrollment.getId()))
                .isInstanceOf(CannotWithdrawException.class);

        assertThat(applicationEvents.stream(EnrollmentWithdrawnForBillingEvent.class)
                .filter(event -> enrollment.getId().equals(event.programEnrollmentId())).count()).isZero();
    }

    @Test
    void courseNonpaymentWithdrawalThroughTheBillingPortWithdrawsTheCourseEnrollmentOnTheGivenDate() {
        Student student = persistStudent();
        CourseEnrollment enrollment = persistCourseEnrollment(student, EnrollmentStatus.ENROLLED);
        LocalDate courseEnd = LocalDate.now().minusDays(20);

        courseNonpaymentWithdrawalPort.withdrawForNonpayment(enrollment.getId(), courseEnd);
        entityManager.flush();
        entityManager.clear();

        CourseEnrollment reloaded = courseEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(reloaded.getWithdrawalEffectiveDate()).isEqualTo(courseEnd);
        assertThat(applicationEvents.stream(EnrollmentWithdrawnForBillingEvent.class)
                .filter(event -> enrollment.getId().equals(event.courseEnrollmentId()))
                .map(EnrollmentWithdrawnForBillingEvent::cause)).containsExactly("NONPAYMENT");
    }

    @Test
    void courseNonpaymentWithdrawalWithAFutureEffectiveDateIsRegisteredWithTodayInstead() {
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent(), EnrollmentStatus.ENROLLED);

        courseNonpaymentWithdrawalPort.withdrawForNonpayment(enrollment.getId(), LocalDate.now().plusMonths(8));
        entityManager.flush();
        entityManager.clear();

        assertThat(courseEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getWithdrawalEffectiveDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void courseNonpaymentWithdrawalWithAnEffectiveDateBeforeTheEnrollmentIsRegisteredOnTheEnrollmentDate() {
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent(), EnrollmentStatus.ENROLLED);
        LocalDate enrolledOn = enrollment.getEnrollmentDate().toLocalDate();

        courseNonpaymentWithdrawalPort.withdrawForNonpayment(enrollment.getId(), enrolledOn.minusMonths(2));
        entityManager.flush();
        entityManager.clear();

        assertThat(courseEnrollmentRepository.findById(enrollment.getId()).orElseThrow().getWithdrawalEffectiveDate()).isEqualTo(enrolledOn);
    }

    @Test
    void courseNonpaymentWithdrawalOfAnAlreadyWithdrawnCourseEnrollmentIsRejected() {
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent(), EnrollmentStatus.WITHDRAWN);

        assertThatThrownBy(() -> courseNonpaymentWithdrawalPort.withdrawForNonpayment(enrollment.getId(), LocalDate.now().minusDays(20)))
                .isInstanceOf(CannotWithdrawException.class);
    }
}
