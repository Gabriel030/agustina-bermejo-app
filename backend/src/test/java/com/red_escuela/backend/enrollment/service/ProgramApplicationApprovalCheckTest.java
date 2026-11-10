package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.collections.exception.OverdueDebtBlocksNewEnrollmentException;
import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse.DocumentationSummary;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-142")
class ProgramApplicationApprovalCheckTest {

    private final ProgramEnrollmentApplicationFinder finder = mock(ProgramEnrollmentApplicationFinder.class);
    private final ProgramApplicationReviewViews views = mock(ProgramApplicationReviewViews.class);
    private final ProgramEnrollmentService enrollmentService = mock(ProgramEnrollmentService.class);
    private final ProgramApplicationApprovalCheck check = new ProgramApplicationApprovalCheck(finder, views, enrollmentService);

    private void givenApplicationOfStudent7(boolean documentationComplete) {
        Student student = new Student();
        student.setId(7L);
        ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
        application.setId(1L);
        application.setStudent(student);
        when(finder.findOrThrow(1L)).thenReturn(application);
        when(views.documentationOf(7L)).thenReturn(new DocumentationSummary(documentationComplete,
                documentationComplete ? List.of() : List.of(DocumentType.DNI_DORSO), List.of()));
    }

    @Test
    void aStudentTheDebtGuardAcceptsWithCompleteDocumentsCanBeApproved() {
        givenApplicationOfStudent7(true);

        ProgramEnrollmentApplicationApprovalCheckResponse response = check.check(1L);

        assertThat(response.canApprove()).isTrue();
        assertThat(response.debt().blocksApproval()).isFalse();
        assertThat(response.debt().violationCodes()).isEmpty();
    }

    @Test
    void anOverdueDebtBlocksApprovalAndExposesTheViolationCodes() {
        givenApplicationOfStudent7(true);
        doThrow(new OverdueDebtBlocksNewEnrollmentException(List.of(new PolicyViolation("OVERDUE_DEBT", "deuda vencida"))))
                .when(enrollmentService).verifyStudentHasNoOutstandingDebt(7L);

        ProgramEnrollmentApplicationApprovalCheckResponse response = check.check(1L);

        assertThat(response.canApprove()).isFalse();
        assertThat(response.debt().blocksApproval()).isTrue();
        assertThat(response.debt().violationCodes()).containsExactly("OVERDUE_DEBT");
    }

    @Test
    void theDebtOfAWithdrawnEnrollmentBlocksToo() {
        givenApplicationOfStudent7(true);
        doThrow(new OutstandingDebtBlocksReenrollmentException(List.of(new PolicyViolation("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT", "deuda"))))
                .when(enrollmentService).verifyStudentHasNoOutstandingDebt(7L);

        assertThat(check.check(1L).debt().violationCodes()).containsExactly("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT");
    }

    @Test
    void missingDocumentsBlockApprovalEvenWithoutDebt() {
        givenApplicationOfStudent7(false);

        ProgramEnrollmentApplicationApprovalCheckResponse response = check.check(1L);

        assertThat(response.canApprove()).isFalse();
        assertThat(response.debt().blocksApproval()).isFalse();
    }

    @Test
    void aConflictThatIsNotADebtBlockIsNotSwallowed() {
        givenApplicationOfStudent7(true);
        doThrow(new com.red_escuela.backend.shared.exception.ConflictException("otro conflicto") { })
                .when(enrollmentService).verifyStudentHasNoOutstandingDebt(7L);

        assertThatThrownBy(() -> check.check(1L)).isInstanceOf(com.red_escuela.backend.shared.exception.ConflictException.class);
    }
}
