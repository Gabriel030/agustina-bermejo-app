package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStatusChangeView;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.support.PaymentStandingSeed;
import com.red_escuela.backend.enrollment.api.EnrollmentReminderTarget;
import com.red_escuela.backend.enrollment.api.EnrollmentReminderTargetFinder;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentPaymentStandingCharacterizationTest extends WithdrawalP2CharacterizationSupport {

    @Autowired
    private EnrollmentPaymentStandingQuery standingQuery;
    @Autowired
    private EnrollmentReminderTargetFinder reminderTargetFinder;

    private ProgramEnrollment programEnrollmentWith(EnrollmentPaymentStatus status) {
        ProgramEnrollment enrollment = persistProgramEnrollment(persistStudent(), persistProgram(), ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        PaymentStandingSeed.seedProgramEnrollmentStanding(jdbc, enrollment.getId(), status);
        return enrollment;
    }

    private CourseEnrollment courseEnrollmentWith(EnrollmentPaymentStatus status) {
        CourseEnrollment enrollment = persistCourseEnrollment(persistStudent(), EnrollmentStatus.ENROLLED);
        entityManager.flush();
        PaymentStandingSeed.seedCourseEnrollmentStanding(jdbc, enrollment.getId(), status);
        return enrollment;
    }

    @Test
    void everyPaymentStatusIsReportedAsStoredForProgramEnrollments() {
        for (EnrollmentPaymentStatus status : EnrollmentPaymentStatus.values()) {
            ProgramEnrollment enrollment = programEnrollmentWith(status);

            Optional<EnrollmentPaymentStandingView> standing = standingQuery.findStandingOfProgramEnrollment(enrollment.getId());

            assertThat(standing).isPresent();
            assertThat(standing.get().status()).isEqualTo(status);
            assertThat(standing.get().enrollmentId()).isEqualTo(enrollment.getId());
            assertThat(standing.get().stopsBilling()).isEqualTo(status == EnrollmentPaymentStatus.SUSPENDED || status == EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        }
    }

    @Test
    void suspendedStandingCarriesSuspendedSinceAndOtherStatusesDoNot() {
        ProgramEnrollment suspended = programEnrollmentWith(EnrollmentPaymentStatus.SUSPENDED);
        ProgramEnrollment overdue = programEnrollmentWith(EnrollmentPaymentStatus.OVERDUE);

        assertThat(standingQuery.findStandingOfProgramEnrollment(suspended.getId()).orElseThrow().suspendedSince()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(standingQuery.findStandingOfProgramEnrollment(overdue.getId()).orElseThrow().suspendedSince()).isNull();
    }

    @Test
    void enrollmentWithoutAStoredStandingHasNoViewAndIsTreatedAsUpToDate() {
        ProgramEnrollment enrollment = persistProgramEnrollment(persistStudent(), persistProgram(), ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        Optional<EnrollmentPaymentStandingView> standing = standingQuery.findStandingOfProgramEnrollment(enrollment.getId());

        assertThat(standing).isEmpty();
        assertThat(EnrollmentPaymentStandingView.statusOrUpToDate(standing.orElse(null))).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(standingQuery.findStatusesOfProgramEnrollments(List.of(enrollment.getId()))).isEmpty();
    }

    @Test
    void courseEnrollmentStandingIsReportedAndBatchStatusesOmitEnrollmentsWithoutStanding() {
        CourseEnrollment overdue = courseEnrollmentWith(EnrollmentPaymentStatus.OVERDUE);
        CourseEnrollment suspended = courseEnrollmentWith(EnrollmentPaymentStatus.SUSPENDED);
        CourseEnrollment withoutStanding = persistCourseEnrollment(persistStudent(), EnrollmentStatus.ENROLLED);
        entityManager.flush();

        Map<Long, EnrollmentPaymentStatus> statuses = standingQuery.findStatusesOfCourseEnrollments(
                List.of(overdue.getId(), suspended.getId(), withoutStanding.getId()));

        assertThat(statuses).containsOnlyKeys(overdue.getId(), suspended.getId());
        assertThat(statuses.get(overdue.getId())).isEqualTo(EnrollmentPaymentStatus.OVERDUE);
        assertThat(statuses.get(suspended.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(standingQuery.findStandingOfCourseEnrollment(suspended.getId()).orElseThrow().studentId()).isEqualTo(suspended.getStudent().getId());
    }

    @Test
    void statusChangeHistoryListsTheRecordedTransitionOfAProgramEnrollment() {
        ProgramEnrollment enrollment = programEnrollmentWith(EnrollmentPaymentStatus.SUSPENDED);

        List<EnrollmentPaymentStatusChangeView> changes = standingQuery.listStatusChangesOfProgramEnrollment(enrollment.getId());

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).toStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId() + 1_000_000)).isEmpty();
    }

    @Test
    void reminderTargetsFlagOnlySuspendedProgramEnrollmentsAndCarryTheProgramId() {
        ProgramEnrollment suspended = programEnrollmentWith(EnrollmentPaymentStatus.SUSPENDED);
        ProgramEnrollment overdue = programEnrollmentWith(EnrollmentPaymentStatus.OVERDUE);
        ProgramEnrollment withdrawn = programEnrollmentWith(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        ProgramEnrollment withoutStanding = persistProgramEnrollment(persistStudent(), persistProgram(), ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        Map<Long, EnrollmentReminderTarget> targets = reminderTargetFinder.findProgramEnrollmentTargets(
                List.of(suspended.getId(), overdue.getId(), withdrawn.getId(), withoutStanding.getId()));

        assertThat(targets).containsOnlyKeys(suspended.getId(), overdue.getId(), withdrawn.getId(), withoutStanding.getId());
        assertThat(targets.get(suspended.getId()).suspended()).isTrue();
        assertThat(targets.get(overdue.getId()).suspended()).isFalse();
        assertThat(targets.get(withdrawn.getId()).suspended()).isFalse();
        assertThat(targets.get(withoutStanding.getId()).suspended()).isFalse();
        assertThat(targets.get(suspended.getId()).programId()).isEqualTo(suspended.getProgram().getId());
        assertThat(targets.get(suspended.getId()).courseId()).isNull();
    }

    @Test
    void reminderTargetsOfCourseEnrollmentsCarryTheCourseIdAndTheSuspendedFlag() {
        CourseEnrollment suspended = courseEnrollmentWith(EnrollmentPaymentStatus.SUSPENDED);
        CourseEnrollment upToDate = courseEnrollmentWith(EnrollmentPaymentStatus.UP_TO_DATE);

        Map<Long, EnrollmentReminderTarget> targets = reminderTargetFinder.findCourseEnrollmentTargets(List.of(suspended.getId(), upToDate.getId()));

        assertThat(targets.get(suspended.getId()).suspended()).isTrue();
        assertThat(targets.get(suspended.getId()).courseId()).isEqualTo(suspended.getCourse().getId());
        assertThat(targets.get(suspended.getId()).programId()).isNull();
        assertThat(targets.get(upToDate.getId()).suspended()).isFalse();
    }

    @Test
    void reminderTargetsOfNoEnrollmentsAreEmptyAndUnknownIdsAreOmitted() {
        assertThat(reminderTargetFinder.findProgramEnrollmentTargets(List.of())).isEmpty();
        assertThat(reminderTargetFinder.findCourseEnrollmentTargets(List.of())).isEmpty();
        assertThat(reminderTargetFinder.findProgramEnrollmentTargets(List.of(987_654_321L))).isEmpty();
    }
}
