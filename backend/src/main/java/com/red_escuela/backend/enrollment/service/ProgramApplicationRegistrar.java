package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.api.ProgramCohortFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.AdmissionFeeAwaitingPaymentException;
import com.red_escuela.backend.enrollment.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.enrollment.exception.DniRequiredException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.events.ProgramApplicationReceivedEvent;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ProgramApplicationRegistrar {

    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ProgramSubjectReferenceFinder programSubjectReferenceFinder;
    private final ProgramEnrollmentService programEnrollmentService;
    private final ProgramCohortFinder programCohortFinder;
    private final CatalogAvailabilityGuard catalogAvailabilityGuard;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ProgramApplicationRegistrar(ProgramEnrollmentApplicationRepository applicationRepository,
                                       ProgramSubjectReferenceFinder programSubjectReferenceFinder,
                                       ProgramEnrollmentService programEnrollmentService,
                                       ProgramCohortFinder programCohortFinder,
                                       CatalogAvailabilityGuard catalogAvailabilityGuard,
                                       Clock clock,
                                       ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.programSubjectReferenceFinder = programSubjectReferenceFinder;
        this.programEnrollmentService = programEnrollmentService;
        this.programCohortFinder = programCohortFinder;
        this.catalogAvailabilityGuard = catalogAvailabilityGuard;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProgramEnrollmentApplication register(Student student, Long programId, Long cohortId) {
        var program = programSubjectReferenceFinder.findProgram(programId);
        catalogAvailabilityGuard.requireProgramListed(program);
        ProgramCohort cohort = programCohortFinder.findOrThrow(cohortId);
        programEnrollmentService.validateAdmissionWindow(cohort);

        applicationRepository
                .findByStudentIdAndProgramIdAndStatus(student.getId(), programId, ProgramEnrollmentApplicationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new AlreadyHasPendingApplicationException(student.getId(), programId);
                });

        if (applicationRepository.existsApprovedWithUnpaidAdmissionInvoice(student.getId(), programId)) {
            throw new AdmissionFeeAwaitingPaymentException(student.getId(), programId);
        }

        requireDni(student);

        ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
        application.setStudent(student);
        application.setProgram(program);
        application.setProgramCohort(cohort);
        application.setStatus(ProgramEnrollmentApplicationStatus.PENDING);
        application.setRequestedAt(LocalDateTime.now(clock));
        ProgramEnrollmentApplication saved = applicationRepository.save(application);
        Person person = student.getPerson();
        eventPublisher.publishEvent(ProgramApplicationReceivedEvent.receivedFor(saved.getId(),
                person.getName() + " " + person.getLastname(), program.getName()));
        return saved;
    }

    private void requireDni(Student student) {
        String dni = student.getPerson().getDni();
        if (dni == null || dni.isBlank()) {
            throw new DniRequiredException(student.getPerson().getId());
        }
    }
}
