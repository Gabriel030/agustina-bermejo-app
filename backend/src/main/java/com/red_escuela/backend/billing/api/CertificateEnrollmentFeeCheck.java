package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CertificateEnrollmentFeeCheck {

    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final EnrollmentFeeRequirementService enrollmentFeeRequirementService;
    private final TransactionTemplate isolatedTransaction;

    public CertificateEnrollmentFeeCheck(ProgramEnrollmentFinder programEnrollmentFinder,
                                         EnrollmentFeeRequirementService enrollmentFeeRequirementService,
                                         PlatformTransactionManager transactionManager) {
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.enrollmentFeeRequirementService = enrollmentFeeRequirementService;
        this.isolatedTransaction = new TransactionTemplate(transactionManager);
        this.isolatedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean isProgramEnrollmentFeeSettled(Long programEnrollmentId) {
        try {
            isolatedTransaction.executeWithoutResult(status -> enrollmentFeeRequirementService.requireForCurrentPeriod(
                    programEnrollmentFinder.requireById(programEnrollmentId), EnrollmentFeeRequirementScope.CERTIFICATE));
            return true;
        } catch (EnrollmentFeeNotPaidException e) {
            return false;
        }
    }
}
