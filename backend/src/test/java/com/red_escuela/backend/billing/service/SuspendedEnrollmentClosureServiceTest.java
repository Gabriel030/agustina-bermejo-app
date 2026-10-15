package com.red_escuela.backend.billing.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.service.YearCycleFactory;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawalDateResolver;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-142")
@Tag("D-147")
@Tag("D-296")
@Tag("D-249")
class SuspendedEnrollmentClosureServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Long ENROLLMENT_ID = 10L;
    private static final Long PROGRAM_ID = 3L;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger closureLogger = (Logger) LoggerFactory.getLogger(SuspendedEnrollmentClosureService.class);
    private final ProgramEnrollmentRepository enrollments = mock(ProgramEnrollmentRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final EnrollmentWithdrawalBillingPort port = mock(EnrollmentWithdrawalBillingPort.class);

    private ProgramEnrollment suspendedEnrollment(LocalDateTime enrolledAt) {
        Student student = new Student();
        student.setId(41L);
        Program program = new Program();
        program.setId(PROGRAM_ID);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setId(ENROLLMENT_ID);
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(enrolledAt);
        enrollment.setPaymentStatus(EnrollmentPaymentStatus.SUSPENDED);
        enrollment.setPaymentStatusUpdatedAt(LocalDateTime.of(2027, 4, 12, 10, 0));
        when(enrollments.findIdsByPaymentStatusAndStatusIn(any(), any())).thenReturn(List.of(ENROLLMENT_ID));
        when(enrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        return enrollment;
    }

    private SuspendedEnrollmentClosureService serviceAt(LocalDateTime localNow) {
        Clock clock = Clock.fixed(localNow.atZone(ZONE).toInstant(), ZONE);
        ProgramYearBillingTemplateRepository templates = mock(ProgramYearBillingTemplateRepository.class);
        ProgramYearBillingTemplate template = mock(ProgramYearBillingTemplate.class);
        when(templates.findAllByProgramId(PROGRAM_ID)).thenReturn(List.of(template));
        YearCycleFactory cycleFactory = mock(YearCycleFactory.class);
        when(template.termYear()).thenReturn(2027);
        YearCycle cycle = YearCycle.of(template, YearBillingTerms.initial(0), 2027);
        when(cycleFactory.cyclesOf(any(), anyInt())).thenReturn(List.of(cycle));
        ProgramEnrollmentService enrollmentService = new ProgramEnrollmentService(enrollments, null, null, null, null, null, null, null, null, null,
                mock(ScholarshipService.class), new EnrollmentWithdrawalDateResolver(clock), port, clock);
        logs.start();
        closureLogger.addAppender(logs);
        return new SuspendedEnrollmentClosureService(enrollments, templates, mock(TermRepository.class), invoices, enrollmentService, clock,
                mock(PlatformTransactionManager.class), cycleFactory);
    }

    @AfterEach
    void detachLogs() {
        closureLogger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void theLastDayOfTheCycleDoesNotCloseTheSuspendedEnrollment() {
        ProgramEnrollment enrollment = suspendedEnrollment(LocalDateTime.of(2027, 3, 1, 10, 0));

        int closed = serviceAt(LocalDateTime.of(2027, 12, 31, 23, 59)).closeFinishedCycles();

        assertThat(closed).isZero();
        assertThat(enrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        assertThat(enrollment.getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        verifyNoInteractions(port);
        verifyNoInteractions(invoices);
    }

    @Test
    void theDayAfterTheCycleClosesItNotifiesBillingWithTheNonpaymentCauseAndTouchesNoInvoice() {
        ProgramEnrollment enrollment = suspendedEnrollment(LocalDateTime.of(2027, 3, 1, 10, 0));
        List<EnrollmentPaymentStatus> paymentStatusWhenNotified = new ArrayList<>();
        doAnswer(invocation -> paymentStatusWhenNotified.add(enrollment.getPaymentStatus())).when(port).registerWithdrawalForBilling(any());

        int closed = serviceAt(LocalDateTime.of(2028, 1, 1, 0, 30)).closeFinishedCycles();

        assertThat(closed).isEqualTo(1);
        assertThat(enrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
        assertThat(enrollment.getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        assertThat(enrollment.getWithdrawnByPersonId()).isNull();
        assertThat(enrollment.getWithdrawalEffectiveDate()).isEqualTo(LocalDate.of(2028, 1, 1));
        verify(port).registerWithdrawalForBilling(nonpaymentOn(LocalDate.of(2028, 1, 1)));
        assertThat(paymentStatusWhenNotified).containsExactly(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        verifyNoInteractions(invoices);
    }

    @Test
    void aWithdrawalRejectedByTheDateRuleIsLoggedAsWarningWithTheEnrollmentAndTheViolationAndChangesNothing() {
        ProgramEnrollment enrollment = suspendedEnrollment(LocalDateTime.of(2028, 6, 1, 10, 0));

        int closed = serviceAt(LocalDateTime.of(2028, 1, 1, 0, 30)).closeFinishedCycles();

        assertThat(closed).isZero();
        assertThat(enrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        verifyNoInteractions(port);
        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("inscripcion=10").contains("WITHDRAWAL_DATE_OUT_OF_ALLOWED_RANGE");
        });
        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.ERROR).isEmpty();
    }

    @Test
    void anUnexpectedFailureIsStillLoggedAsErrorWithTheStackTrace() {
        suspendedEnrollment(LocalDateTime.of(2027, 3, 1, 10, 0));
        doAnswer(invocation -> {
            throw new IllegalStateException("boom");
        }).when(port).registerWithdrawalForBilling(any());

        int closed = serviceAt(LocalDateTime.of(2028, 1, 1, 0, 30)).closeFinishedCycles();

        assertThat(closed).isZero();
        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.ERROR).singleElement()
                .satisfies(event -> assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom"));
    }

    private static WithdrawnEnrollmentFacts nonpaymentOn(LocalDate date) {
        return WithdrawnEnrollmentFacts.ofProgramEnrollment(41L, ENROLLMENT_ID, date, WithdrawalCause.NONPAYMENT);
    }
}
