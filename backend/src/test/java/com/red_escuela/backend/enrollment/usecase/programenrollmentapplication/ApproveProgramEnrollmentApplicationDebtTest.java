package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
import com.red_escuela.backend.billing.invoice.service.ProgramAdmissionInvoiceIssuer;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.PersonRoleGranter;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-142")
@Tag("C-16")
class ApproveProgramEnrollmentApplicationDebtTest {

    @Test
    void approvingAnApplicationOfAStudentWithOutstandingDebtIsRejectedBeforeIssuingTheAdmissionInvoice() {
        ProgramEnrollmentApplicationFinder finder = mock(ProgramEnrollmentApplicationFinder.class);
        ProgramAdmissionInvoiceIssuer issuer = mock(ProgramAdmissionInvoiceIssuer.class);
        ProgramEnrollmentService enrollmentService = mock(ProgramEnrollmentService.class);
        PersonRoleGranter roleGranter = mock(PersonRoleGranter.class);
        ProgramEnrollmentApplication application = mock(ProgramEnrollmentApplication.class);
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(7L);
        when(application.getStatus()).thenReturn(ProgramEnrollmentApplicationStatus.PENDING);
        when(application.getStudent()).thenReturn(student);
        when(finder.findOrThrow(anyLong())).thenReturn(application);
        doThrow(new OutstandingDebtBlocksReenrollmentException(List.of(new PolicyViolation("OVERDUE_INVOICE", "deuda"))))
                .when(enrollmentService).verifyStudentHasNoOutstandingDebt(7L);
        ApproveProgramEnrollmentApplicationService useCase = new ApproveProgramEnrollmentApplicationService(finder, roleGranter, issuer, enrollmentService,
                mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> useCase.approve(new ApproveProgramEnrollmentApplicationCommand(1L, mock(Person.class))))
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);

        verify(enrollmentService).verifyStudentHasNoOutstandingDebt(7L);
        verifyNoInteractions(issuer);
        verifyNoInteractions(roleGranter);
    }
}
