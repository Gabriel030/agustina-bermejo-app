package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.ProgramAdmissionInvoiceIssuer;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.InvalidProgramEnrollmentApplicationStateException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Service;
import com.red_escuela.backend.enrollment.events.ProgramApplicationApprovedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ApproveProgramEnrollmentApplicationTransaction {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final ProgramApplicantRoleGranter programApplicantRoleGranter;
    private final ProgramAdmissionInvoiceIssuer programAdmissionInvoiceIssuer;
    private final ProgramEnrollmentService programEnrollmentService;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveProgramEnrollmentApplicationTransaction(ProgramEnrollmentApplicationFinder applicationFinder,
                                                        ProgramApplicantRoleGranter programApplicantRoleGranter,
                                                        ProgramAdmissionInvoiceIssuer programAdmissionInvoiceIssuer,
                                                        ProgramEnrollmentService programEnrollmentService,
                                                        ApplicationEventPublisher eventPublisher) {
        this.applicationFinder = applicationFinder;
        this.programApplicantRoleGranter = programApplicantRoleGranter;
        this.programAdmissionInvoiceIssuer = programAdmissionInvoiceIssuer;
        this.programEnrollmentService = programEnrollmentService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProgramEnrollmentApplication approve(Long applicationId, String rawIdempotencyKey, Person decidedBy) {
        String idempotencyKey = ApplicationDecisionIdempotencyKeys.normalize(rawIdempotencyKey);
        ProgramEnrollmentApplication application = applicationFinder.findLockedOrThrow(applicationId);
        if (ApplicationDecisionIdempotencyKeys.isReplayOfApproval(application, idempotencyKey)) {
            return application;
        }
        if (application.getStatus() != ProgramEnrollmentApplicationStatus.PENDING) {
            throw new InvalidProgramEnrollmentApplicationStateException(application.getId(), application.getStatus());
        }
        Student student = application.getStudent();
        programEnrollmentService.validateStudentCanEnrollInProgram(student, application.getProgram());
        programEnrollmentService.verifyStudentHasNoOutstandingDebt(student.getId());

        Invoice admissionInvoice = programAdmissionInvoiceIssuer.issueAdmissionInvoice(student.getId(), application.getProgram(), application.getProgramCohort());
        application.approve(decidedBy, admissionInvoice);
        ApplicationDecisionIdempotencyKeys.remember(application, idempotencyKey);
        programApplicantRoleGranter.grantStudentRole(student);
        eventPublisher.publishEvent(ProgramApplicationApprovedEvent.approvedFor(application.getId(), student.getPerson().getId(), application.getProgram().getName()));
        return application;
    }
}
