package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RejectProgramEnrollmentApplicationService implements RejectProgramEnrollmentApplicationUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;

    public RejectProgramEnrollmentApplicationService(ProgramEnrollmentApplicationFinder applicationFinder) {
        this.applicationFinder = applicationFinder;
    }

    @Override
    @Transactional
    public ProgramEnrollmentApplication reject(RejectProgramEnrollmentApplicationCommand command) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(command.id());
        application.reject(command.decidedBy(), command.reason());
        discardEnrollmentFeePayment(application.getPaymentReceipt(), command);
        return application;
    }

    private void discardEnrollmentFeePayment(BankTransferReceipt receipt, RejectProgramEnrollmentApplicationCommand command) {
        if (receipt == null) {
            return;
        }
        if (receipt.isPendingReview()) {
            receipt.reject(command.decidedBy(), command.reason());
        }
    }
}
