package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.graduation.service.GraduationDebtGate;
import com.red_escuela.backend.graduation.service.GraduationRequestApprover;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GraduationDebtGateUseCasesTest {

    private static final Long REQUEST_ID = 7L;
    private static final Long STUDENT_ID = 11L;

    private final GraduationRequestFinder finder = Mockito.mock(GraduationRequestFinder.class);
    private final GraduationRequestRepository repository = Mockito.mock(GraduationRequestRepository.class);
    private final GraduationRequestApprover approver = Mockito.mock(GraduationRequestApprover.class);
    private final GraduationDebtGate debtGate = Mockito.mock(GraduationDebtGate.class);
    private final InvoiceQueryService invoiceQueryService = Mockito.mock(InvoiceQueryService.class);
    private final CurrentPersonService currentPersonService = Mockito.mock(CurrentPersonService.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-03-01T10:00:00Z"), ZoneOffset.UTC);
    private GraduationRequest request;

    @BeforeEach
    void prepareRequest() {
        Student student = new Student();
        student.setId(STUDENT_ID);
        student.setPerson(new Person());
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        com.red_escuela.backend.academicstructure.entity.Program program = new com.red_escuela.backend.academicstructure.entity.Program();
        program.setName("Carrera");
        enrollment.setProgram(program);
        request = new GraduationRequest();
        request.setId(REQUEST_ID);
        request.setProgramEnrollment(enrollment);
        request.setStatus(GraduationRequestStatus.REQUESTED);
        Mockito.when(finder.findOrThrow(REQUEST_ID)).thenReturn(request);
        Mockito.when(repository.save(Mockito.any())).thenAnswer(call -> call.getArgument(0));
        Mockito.when(approver.requireApprovableAndFindStudentId(request)).thenReturn(STUDENT_ID);
        Mockito.when(invoiceQueryService.hasDebt(STUDENT_ID)).thenReturn(true);
    }

    private VerifyGraduationRequestDebtService verifyService() {
        return new VerifyGraduationRequestDebtService(repository, finder, debtGate, invoiceQueryService, currentPersonService, eventPublisher, clock);
    }

    private ApproveGraduationRequestService approveService() {
        return new ApproveGraduationRequestService(finder, approver, debtGate, invoiceQueryService, currentPersonService, eventPublisher, clock);
    }

    @Test
    void verifyingWhileBillingIsOffSkipsTheDebtLookupAndVerifiesTheRequest() {
        Mockito.when(debtGate.mustCheckDebt()).thenReturn(false);

        GraduationRequest verified = verifyService().verify(REQUEST_ID);

        assertThat(verified.getStatus()).isEqualTo(GraduationRequestStatus.ADMIN_VERIFIED);
        Mockito.verifyNoInteractions(invoiceQueryService);
    }

    @Test
    void approvingWhileBillingIsOffSkipsTheDebtLookup() {
        Mockito.when(debtGate.mustCheckDebt()).thenReturn(false);

        approveService().approve(REQUEST_ID);

        Mockito.verify(approver).recordApproval(Mockito.eq(request), Mockito.any(), Mockito.any());
        Mockito.verifyNoInteractions(invoiceQueryService);
    }

    @Test
    void verifyingWhileBillingIsOnStillBlocksOnDebt() {
        Mockito.when(debtGate.mustCheckDebt()).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> verifyService().verify(REQUEST_ID))
                .isInstanceOf(com.red_escuela.backend.billing.exception.PendingDebtException.class);
        assertThat(request.getStatus()).isEqualTo(GraduationRequestStatus.REQUESTED);
    }
}
