package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.PersonRoleGranter;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ApproveProgramEnrollmentApplicationService implements ApproveProgramEnrollmentApplicationUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final ProgramEnrollmentService programEnrollmentService;
    private final PersonRoleGranter personRoleGranter;
    private final ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;

    public ApproveProgramEnrollmentApplicationService(ProgramEnrollmentApplicationFinder applicationFinder,
                                                        ProgramEnrollmentService programEnrollmentService,
                                                        PersonRoleGranter personRoleGranter,
                                                        ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase) {
        this.applicationFinder = applicationFinder;
        this.programEnrollmentService = programEnrollmentService;
        this.personRoleGranter = personRoleGranter;
        this.approvePaymentReceiptUseCase = approvePaymentReceiptUseCase;
    }

    @Override
    @Transactional
    public ProgramEnrollmentApplication approve(ApproveProgramEnrollmentApplicationCommand command) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(command.id());
        application.approve(command.decidedBy());

        Student student = application.getStudent();
        Long cohortId = application.getProgramCohort() == null ? null : application.getProgramCohort().getId();
        ProgramEnrollment enrollment = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(application.getProgram().getId(), cohortId));
        personRoleGranter.grantStudentRoleIfMissing(student.getPerson());

        if (application.getPaymentReceipt() != null) {
            application.getPaymentReceipt().getInvoice().setProgramEnrollment(enrollment);
        }

        if (application.getPaymentReceipt() != null && application.getPaymentReceipt().isPendingReview()) {
            approvePaymentReceiptUseCase.approve(application.getPaymentReceipt().getId(), command.decidedBy());
        }

        return application;
    }
}
