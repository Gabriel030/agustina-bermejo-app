package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.billing.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.billing.api.WithdrawalCause;
import com.red_escuela.backend.billing.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.enrollment.dto.ProgramProgressResponse;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-236")
@Tag("D-296")
class ApproveGraduationRequestBillingNotificationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private final Clock clock = Clock.fixed(LocalDateTime.of(2027, 10, 20, 10, 0).atZone(ZONE).toInstant(), ZONE);
    private final EnrollmentWithdrawalBillingPort port = mock(EnrollmentWithdrawalBillingPort.class);
    private final InvoiceQueryService invoiceQueryService = mock(InvoiceQueryService.class);
    private final GraduationRequest request = mock(GraduationRequest.class);
    private ApproveGraduationRequestService service;

    @BeforeEach
    void setUp() {
        ProgramEnrollmentService programEnrollmentService = mock(ProgramEnrollmentService.class);
        GraduationRequestFinder finder = mock(GraduationRequestFinder.class);
        CurrentPersonService currentPersonService = mock(CurrentPersonService.class);
        Student student = new Student();
        student.setId(41L);
        student.setPerson(new Person());
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setId(10L);
        enrollment.setStudent(student);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        when(finder.findOrThrow(3L)).thenReturn(request);
        when(request.getProgramEnrollment()).thenReturn(enrollment);
        ProgramProgressResponse progress = mock(ProgramProgressResponse.class);
        when(progress.elegibleParaCompletar()).thenReturn(true);
        when(programEnrollmentService.calculateProgress(10L)).thenReturn(progress);
        when(currentPersonService.resolveCurrentPerson()).thenReturn(new Person());
        service = new ApproveGraduationRequestService(mock(GraduationRequestRepository.class), mock(ProgramEnrollmentRepository.class), finder,
                programEnrollmentService, invoiceQueryService, currentPersonService, mock(ApplicationEventPublisher.class), port, clock);
    }

    @Test
    void approvingAGraduationNotifiesBillingWithTheGraduationCauseAndTodayAsEffectiveDate() {
        service.approve(3L);

        verify(port).registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofProgramEnrollment(41L, 10L, LocalDate.of(2027, 10, 20), WithdrawalCause.GRADUATION));
    }

    @Test
    void graduationRejectedForDebtDoesNotNotifyBilling() {
        when(invoiceQueryService.hasDebt(41L)).thenReturn(true);

        assertThatThrownBy(() -> service.approve(3L)).isInstanceOf(PendingDebtException.class);

        verifyNoInteractions(port);
    }
}
