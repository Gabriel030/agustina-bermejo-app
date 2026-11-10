package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.collections.exception.OverdueDebtBlocksNewEnrollmentException;
import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class EnrollmentDebtBlockPreview {

    private final EnrollmentDebtGuard enrollmentDebtGuard;

    public EnrollmentDebtBlockPreview(EnrollmentDebtGuard enrollmentDebtGuard) {
        this.enrollmentDebtGuard = enrollmentDebtGuard;
    }

    public EnrollmentDebtBlockView previewFor(Long studentId) {
        try {
            enrollmentDebtGuard.verifyNoOutstandingDebt(studentId);
            return EnrollmentDebtBlockView.clear();
        } catch (OutstandingDebtBlocksReenrollmentException blocked) {
            return blockedBy(blocked);
        } catch (OverdueDebtBlocksNewEnrollmentException blocked) {
            return blockedBy(blocked);
        }
    }

    private EnrollmentDebtBlockView blockedBy(ReportsPolicyViolations blocked) {
        return EnrollmentDebtBlockView.blockedBy(blocked.violations().stream().map(PolicyViolation::code).toList());
    }
}
