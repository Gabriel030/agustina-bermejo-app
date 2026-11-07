package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentClosedWhileSuspendedEvent;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.SuspendedEnrollmentClosureReport;
import com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentStanding;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.collections.repository.EnrollmentPaymentStandingRepository;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentEndView;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.api.CourseEnrollmentNonpaymentWithdrawalPort;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-123")
@Tag("D-142")
class CourseEnrollmentWithdrawalAtCourseEndServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate COURSE_END = LocalDate.of(2027, 6, 30);

    private final EnrollmentPaymentStandingRepository standingRepository = mock(EnrollmentPaymentStandingRepository.class);
    private final CourseEnrollmentFinder finder = mock(CourseEnrollmentFinder.class);
    private final CourseEnrollmentNonpaymentWithdrawalPort courseEnrollmentService = mock(CourseEnrollmentNonpaymentWithdrawalPort.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private CourseEnrollmentWithdrawalAtCourseEndService serviceOn(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(1, 0).atZone(ZONE).toInstant(), ZONE);
        return new CourseEnrollmentWithdrawalAtCourseEndService(standingRepository, finder, courseEnrollmentService, eventPublisher, clock, mock(PlatformTransactionManager.class));
    }

    private void suspended(CourseEnrollmentEndView... views) {
        for (CourseEnrollmentEndView view : views) {
            standingWith(view.enrollmentId(), EnrollmentPaymentStatus.SUSPENDED);
        }
        when(standingRepository.findSuspendedCourseEnrollmentIds()).thenReturn(java.util.Arrays.stream(views).map(CourseEnrollmentEndView::enrollmentId).toList());
        when(finder.findEndViewsByIds(anyCollection())).thenReturn(List.of(views));
    }

    private void standingWith(Long enrollmentId, EnrollmentPaymentStatus status) {
        EnrollmentPaymentStanding standing = mock(EnrollmentPaymentStanding.class);
        when(standing.getStatus()).thenReturn(status);
        when(standingRepository.findByCourseEnrollmentForUpdate(enrollmentId)).thenReturn(Optional.of(standing));
    }

    private static CourseEnrollmentEndView view(Long id, EnrollmentStatus status, LocalDate courseEnd) {
        return new CourseEnrollmentEndView(id, status, courseEnd);
    }

    @Test
    void theDayAfterTheCourseEndsTheSuspendedEnrollmentIsWithdrawnForNonpaymentAndTheStandingEventIsPublished() {
        suspended(view(7L, EnrollmentStatus.ENROLLED, COURSE_END));
        LocalDate today = COURSE_END.plusDays(1);

        SuspendedEnrollmentClosureReport report = serviceOn(today).withdrawSuspendedEnrollmentsOfEndedCourses();

        assertThat(report.candidates()).isEqualTo(1);
        assertThat(report.closed()).isEqualTo(1);
        assertThat(report.failures()).isEmpty();
        verify(courseEnrollmentService).withdrawForNonpayment(7L, COURSE_END);
        verify(eventPublisher).publishEvent(ArgumentMatchers.<Object>argThat(published -> published instanceof EnrollmentClosedWhileSuspendedEvent event
                && event.kind() == EnrollmentKind.COURSE && event.enrollmentId().equals(7L) && event.businessDate().equals(today)));
    }

    @Test
    void theLastDayOfTheCourseDoesNotWithdrawAnything() {
        suspended(view(7L, EnrollmentStatus.ENROLLED, COURSE_END));

        SuspendedEnrollmentClosureReport report = serviceOn(COURSE_END).withdrawSuspendedEnrollmentsOfEndedCourses();

        assertThat(report.candidates()).isZero();
        verifyNoInteractions(courseEnrollmentService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aCourseWithoutEndDateIsNeverClosed() {
        suspended(view(7L, EnrollmentStatus.ENROLLED, null));

        SuspendedEnrollmentClosureReport report = serviceOn(COURSE_END.plusYears(5)).withdrawSuspendedEnrollmentsOfEndedCourses();

        assertThat(report.candidates()).isZero();
        verifyNoInteractions(courseEnrollmentService);
    }

    @Test
    void enrollmentsThatAreNotActiveAnymoreAreNotCandidates() {
        suspended(view(1L, EnrollmentStatus.WITHDRAWN, COURSE_END), view(2L, EnrollmentStatus.APPROVED, COURSE_END), view(3L, EnrollmentStatus.WAITLISTED, COURSE_END));

        SuspendedEnrollmentClosureReport report = serviceOn(COURSE_END.plusDays(1)).withdrawSuspendedEnrollmentsOfEndedCourses();

        assertThat(report.candidates()).isZero();
        verifyNoInteractions(courseEnrollmentService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aStudentWhoPaidBetweenTheSelectionAndTheirTurnIsSkippedSilentlyWithoutFailure() {
        suspended(view(7L, EnrollmentStatus.ENROLLED, COURSE_END), view(9L, EnrollmentStatus.ENROLLED, COURSE_END));
        standingWith(7L, EnrollmentPaymentStatus.UP_TO_DATE);

        SuspendedEnrollmentClosureReport report = serviceOn(COURSE_END.plusDays(1)).withdrawSuspendedEnrollmentsOfEndedCourses();

        assertThat(report.candidates()).isEqualTo(2);
        assertThat(report.closed()).isEqualTo(1);
        assertThat(report.failures()).isEmpty();
        verify(courseEnrollmentService, never()).withdrawForNonpayment(7L, COURSE_END);
        verify(courseEnrollmentService).withdrawForNonpayment(9L, COURSE_END);
        verify(eventPublisher, never()).publishEvent(ArgumentMatchers.<Object>argThat(published -> published instanceof EnrollmentClosedWhileSuspendedEvent event && event.enrollmentId().equals(7L)));
    }

    @Test
    void noSuspendedEnrollmentsMeansNoWork() {
        suspended();

        SuspendedEnrollmentClosureReport report = serviceOn(COURSE_END.plusDays(1)).withdrawSuspendedEnrollmentsOfEndedCourses();

        assertThat(report.candidates()).isZero();
        assertThat(report.closed()).isZero();
        verifyNoInteractions(courseEnrollmentService);
    }

    @Test
    void anUnexpectedFailureIsReportedAsFailedAndTheNextEnrollmentIsStillWithdrawn() {
        suspended(view(8L, EnrollmentStatus.ENROLLED, COURSE_END), view(9L, EnrollmentStatus.ENROLLED, COURSE_END));
        doThrow(new IllegalStateException("boom")).when(courseEnrollmentService).withdrawForNonpayment(8L, COURSE_END);

        SuspendedEnrollmentClosureReport report = serviceOn(COURSE_END.plusDays(1)).withdrawSuspendedEnrollmentsOfEndedCourses();

        assertThat(report.candidates()).isEqualTo(2);
        assertThat(report.closed()).isEqualTo(1);
        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.kind()).isEqualTo(EnrollmentKind.COURSE);
            assertThat(failure.enrollmentId()).isEqualTo(8L);
            assertThat(failure.rejectedByRule()).isFalse();
            assertThat(failure.code()).isEqualTo("SUBJECT_PROCESSING_FAILED");
            assertThat(failure.message()).isEqualTo("boom");
        });
        verify(courseEnrollmentService).withdrawForNonpayment(9L, COURSE_END);
        verify(eventPublisher, never()).publishEvent(ArgumentMatchers.<Object>argThat(published -> published instanceof EnrollmentClosedWhileSuspendedEvent event && event.enrollmentId().equals(8L)));
    }

    @Test
    void aRuleRejectionIsReportedAsSkippedWithItsViolationCodes() {
        suspended(view(8L, EnrollmentStatus.ENROLLED, COURSE_END));
        doThrow(new RuleRejection()).when(courseEnrollmentService).withdrawForNonpayment(8L, COURSE_END);

        SuspendedEnrollmentClosureReport report = serviceOn(COURSE_END.plusDays(1)).withdrawSuspendedEnrollmentsOfEndedCourses();

        assertThat(report.closed()).isZero();
        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.rejectedByRule()).isTrue();
            assertThat(failure.code()).isEqualTo("WITHDRAWAL_REJECTED_BY_RULE");
            assertThat(failure.message()).isEqualTo("RULE_A,RULE_B");
        });
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void runningAgainAfterTheEnrollmentWasWithdrawnFindsNoCandidate() {
        suspended(view(7L, EnrollmentStatus.ENROLLED, COURSE_END));
        CourseEnrollmentWithdrawalAtCourseEndService service = serviceOn(COURSE_END.plusDays(1));
        service.withdrawSuspendedEnrollmentsOfEndedCourses();
        suspended(view(7L, EnrollmentStatus.WITHDRAWN, COURSE_END));

        SuspendedEnrollmentClosureReport retry = service.withdrawSuspendedEnrollmentsOfEndedCourses();

        assertThat(retry.candidates()).isZero();
        verify(courseEnrollmentService).withdrawForNonpayment(7L, COURSE_END);
    }

    private static final class RuleRejection extends RuntimeException implements ReportsPolicyViolations {
        @Override
        public List<PolicyViolation> violations() {
            return List.of(new PolicyViolation("RULE_A", "a"), new PolicyViolation("RULE_B", "b"));
        }
    }
}
