package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.exception.DuplicateChargeException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingRequestFactory;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.service.StudentDebtResolutionService;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.InvalidProgramEnrollmentApplicationStateException;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.PersonRoleGranter;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@UseCase
public class ApproveProgramEnrollmentApplicationService implements ApproveProgramEnrollmentApplicationUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final PersonRoleGranter personRoleGranter;
    private final InvoiceIssuingService invoiceIssuingService;
    private final StudentDebtResolutionService studentDebtResolutionService;
    private final Clock clock;

    public ApproveProgramEnrollmentApplicationService(ProgramEnrollmentApplicationFinder applicationFinder,
                                                        PersonRoleGranter personRoleGranter,
                                                        InvoiceIssuingService invoiceIssuingService,
                                                        StudentDebtResolutionService studentDebtResolutionService,
                                                        Clock clock) {
        this.applicationFinder = applicationFinder;
        this.personRoleGranter = personRoleGranter;
        this.invoiceIssuingService = invoiceIssuingService;
        this.studentDebtResolutionService = studentDebtResolutionService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProgramEnrollmentApplication approve(ApproveProgramEnrollmentApplicationCommand command) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(command.id());
        if (application.getStatus() != ProgramEnrollmentApplicationStatus.PENDING) {
            throw new InvalidProgramEnrollmentApplicationStateException(application.getId(), application.getStatus());
        }
        Student student = application.getStudent();
        studentDebtResolutionService.verifyNoDebt(student.getId());

        Invoice admissionInvoice = issueOrFindAdmissionInvoice(application);
        application.approve(command.decidedBy(), admissionInvoice);
        personRoleGranter.grantStudentRoleIfMissing(student.getPerson());
        return application;
    }

    private Invoice issueOrFindAdmissionInvoice(ProgramEnrollmentApplication application) {
        return switch (invoiceIssuingService.issueInvoiceInCallerTransaction(InvoiceIssuingRequestFactory.createProgramAdmissionRequest(
                application.getStudent().getId(), application.getProgram(), application.getProgramCohort(), LocalDate.now(clock)))) {
            case InvoiceIssuingResult.InvoiceIssued issued -> issued.invoice();
            case InvoiceIssuingResult.InvoiceAlreadyIssued existing -> throw new DuplicateChargeException("admission");
            case InvoiceIssuingResult.InvoiceIssuingRejected rejected -> throw new ChargeRejectedException(rejected.evaluationResult());
        };
    }
}
