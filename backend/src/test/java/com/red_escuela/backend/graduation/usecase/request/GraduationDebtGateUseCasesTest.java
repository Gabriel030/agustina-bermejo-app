package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.specialist.GraduationDebtGate;
import com.red_escuela.backend.graduation.specialist.GraduationRequestApprover;
import com.red_escuela.backend.graduation.specialist.GraduationRequestFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.graduation.specialist.GraduationDecisionRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;


import static org.assertj.core.api.Assertions.assertThat;

class GraduationDebtGateUseCasesTest {

    private static final Long REQUEST_ID = 7L;
    private static final Long STUDENT_ID = 11L;

    private final GraduationRequestFinder finder = Mockito.mock(GraduationRequestFinder.class);
    private final GraduationRequestApprover approver = Mockito.mock(GraduationRequestApprover.class);
    private final GraduationDebtGate debtGate = Mockito.mock(GraduationDebtGate.class);
    private final InvoiceQueryService invoiceQueryService = Mockito.mock(InvoiceQueryService.class);
    private final GraduationDecisionRecorder decisionRecorder = Mockito.mock(GraduationDecisionRecorder.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    private final com.red_escuela.backend.graduation.specialist.GraduationDebtBlockPublisher debtBlockPublisher = Mockito.mock(com.red_escuela.backend.graduation.specialist.GraduationDebtBlockPublisher.class);
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
        Mockito.when(decisionRecorder.recordVerification(request)).thenAnswer(call -> {
            request.setStatus(GraduationRequestStatus.ADMIN_VERIFIED);
            return request;
        });
        Mockito.when(approver.requireApprovableAndFindStudentId(request)).thenReturn(STUDENT_ID);
        Mockito.when(invoiceQueryService.hasDebt(STUDENT_ID)).thenReturn(true);
        Mockito.when(invoiceQueryService.debtOf(STUDENT_ID)).thenReturn(new com.red_escuela.backend.billing.api.StudentDebtView(
                com.red_escuela.backend.shared.domain.Money.of(new java.math.BigDecimal("33000.00"), com.red_escuela.backend.shared.domain.Currency.ARS),
                java.util.List.of(
                        new com.red_escuela.backend.billing.api.StudentDebtInstallmentView(java.time.YearMonth.of(2026, 3), java.time.LocalDate.of(2026, 3, 10),
                                com.red_escuela.backend.shared.domain.Money.of(new java.math.BigDecimal("15000.00"), com.red_escuela.backend.shared.domain.Currency.ARS), "OVERDUE"),
                        new com.red_escuela.backend.billing.api.StudentDebtInstallmentView(java.time.YearMonth.of(2026, 4), java.time.LocalDate.of(2026, 4, 10),
                                com.red_escuela.backend.shared.domain.Money.of(new java.math.BigDecimal("18000.00"), com.red_escuela.backend.shared.domain.Currency.ARS), "OVERDUE"))));
    }

    private com.red_escuela.backend.graduation.specialist.GraduationDebtChecker debtChecker() {
        return new com.red_escuela.backend.graduation.specialist.GraduationDebtChecker(debtGate, invoiceQueryService, finder, debtBlockPublisher);
    }

    private VerifyGraduationRequestDebtService verifyService() {
        return new VerifyGraduationRequestDebtService(finder, debtChecker(), decisionRecorder, eventPublisher);
    }

    private ApproveGraduationRequestService approveService() {
        return new ApproveGraduationRequestService(finder, approver, debtChecker(), decisionRecorder, eventPublisher);
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

        Mockito.verify(decisionRecorder).recordApproval(request);
        Mockito.verifyNoInteractions(invoiceQueryService);
    }

    @Test
    void verifyingWhileBillingIsOnStillBlocksOnDebt() {
        Mockito.when(debtGate.mustCheckDebt()).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> verifyService().verify(REQUEST_ID))
                .isInstanceOf(com.red_escuela.backend.billing.exception.PendingDebtException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((com.red_escuela.backend.billing.exception.PendingDebtException) error).details())
                        .containsEntry("totalDue", new java.math.BigDecimal("33000.00")).containsEntry("installmentCount", 2));
        assertThat(request.getStatus()).isEqualTo(GraduationRequestStatus.REQUESTED);
        Mockito.verify(debtBlockPublisher).publishDebtBlocked(Mockito.argThat(event -> event.requestId().equals(REQUEST_ID) && event.installmentCount() == 2));
    }

    @Test
    void verifyingWithoutDebtDoesNotPublishTheDebtBlockedEvent() {
        Mockito.when(debtGate.mustCheckDebt()).thenReturn(true);
        Mockito.when(invoiceQueryService.hasDebt(STUDENT_ID)).thenReturn(false);

        verifyService().verify(REQUEST_ID);

        Mockito.verifyNoInteractions(debtBlockPublisher);
    }
}
