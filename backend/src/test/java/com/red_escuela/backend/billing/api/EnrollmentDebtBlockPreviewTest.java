package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.collections.exception.OverdueDebtBlocksNewEnrollmentException;
import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@Tag("D-142")
class EnrollmentDebtBlockPreviewTest {

    private final EnrollmentDebtGuard guard = mock(EnrollmentDebtGuard.class);
    private final EnrollmentDebtBlockPreview preview = new EnrollmentDebtBlockPreview(guard);

    @Test
    void aStudentTheGuardAcceptsIsNotBlocked() {
        EnrollmentDebtBlockView view = preview.previewFor(7L);

        assertThat(view.blocked()).isFalse();
        assertThat(view.violationCodes()).isEmpty();
    }

    @Test
    void anOverdueDebtBlockExposesTheViolationCodes() {
        doThrow(new OverdueDebtBlocksNewEnrollmentException(List.of(new PolicyViolation("OVERDUE_DEBT", "deuda vencida"))))
                .when(guard).verifyNoOutstandingDebt(7L);

        EnrollmentDebtBlockView view = preview.previewFor(7L);

        assertThat(view.blocked()).isTrue();
        assertThat(view.violationCodes()).containsExactly("OVERDUE_DEBT");
    }

    @Test
    void anOutstandingDebtOfAWithdrawnEnrollmentBlocksToo() {
        doThrow(new OutstandingDebtBlocksReenrollmentException(List.of(new PolicyViolation("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT", "deuda"))))
                .when(guard).verifyNoOutstandingDebt(7L);

        EnrollmentDebtBlockView view = preview.previewFor(7L);

        assertThat(view.blocked()).isTrue();
        assertThat(view.violationCodes()).containsExactly("OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT");
    }
}
