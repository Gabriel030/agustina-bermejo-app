package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.ProgramAdmissionInvoiceIssuer;
import com.red_escuela.backend.billing.service.StudentDebtResolutionService;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.InvalidProgramEnrollmentApplicationStateException;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.service.PersonRoleGranter;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.enrollment.events.ProgramApplicationApprovedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class ApproveProgramEnrollmentApplicationService implements ApproveProgramEnrollmentApplicationUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final PersonRoleGranter personRoleGranter;
    private final ProgramAdmissionInvoiceIssuer programAdmissionInvoiceIssuer;
    private final StudentDebtResolutionService studentDebtResolutionService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveProgramEnrollmentApplicationService(ProgramEnrollmentApplicationFinder applicationFinder,
                                                        PersonRoleGranter personRoleGranter,
                                                        ProgramAdmissionInvoiceIssuer programAdmissionInvoiceIssuer,
                                                        StudentDebtResolutionService studentDebtResolutionService,
                                                        ProgramEnrollmentService programEnrollmentService,
                                                        ApplicationEventPublisher eventPublisher) {
        this.applicationFinder = applicationFinder;
        this.personRoleGranter = personRoleGranter;
        this.programAdmissionInvoiceIssuer = programAdmissionInvoiceIssuer;
        this.studentDebtResolutionService = studentDebtResolutionService;
        this.programEnrollmentService = programEnrollmentService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ProgramEnrollmentApplication approve(ApproveProgramEnrollmentApplicationCommand command) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(command.id());
        if (application.getStatus() != ProgramEnrollmentApplicationStatus.PENDING) {
            throw new InvalidProgramEnrollmentApplicationStateException(application.getId(), application.getStatus());
        }
        Student student = application.getStudent();
        programEnrollmentService.validateStudentCanEnrollInProgram(student, application.getProgram());
        studentDebtResolutionService.verifyNoDebt(student.getId());

        Invoice admissionInvoice = programAdmissionInvoiceIssuer.issueAdmissionInvoice(student.getId(), application.getProgram(), application.getProgramCohort());
        application.approve(command.decidedBy(), admissionInvoice);
        personRoleGranter.grantStudentRoleIfMissing(student.getPerson());
        eventPublisher.publishEvent(ProgramApplicationApprovedEvent.approvedFor(application.getId(), student.getPerson().getId(), application.getProgram().getName()));
        return application;
    }
}
