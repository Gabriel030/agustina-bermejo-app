package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import java.time.Clock;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.service.ProgramCohortService;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.exception.DniRequiredException;
import com.red_escuela.backend.enrollment.exception.AdmissionFeeAwaitingPaymentException;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class RequestProgramEnrollmentApplicationService implements RequestProgramEnrollmentApplicationUseCase {

    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final ProgramCohortService programCohortService;
    private final Clock clock;

    public RequestProgramEnrollmentApplicationService(ProgramEnrollmentApplicationRepository applicationRepository,
                                                        ProgramRepository programRepository,
                                                        ProgramEnrollmentService programEnrollmentService,
                                                        ProgramCohortService programCohortService,
                                                        Clock clock) {
        this.applicationRepository = applicationRepository;
        this.programRepository = programRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.programCohortService = programCohortService;
        this.clock = clock;
    }

    private void requireDni(Student student) {
        String dni = student.getPerson().getDni();
        if (dni == null || dni.isBlank()) {
            throw new DniRequiredException(student.getPerson().getId());
        }
    }

    @Override
    @Transactional
    public ProgramEnrollmentApplication request(RequestProgramEnrollmentApplicationCommand command) {
        Student student = command.student();
        Program program = programRepository.findById(command.programId())
                .orElseThrow(() -> new ProgramNotFoundException(command.programId()));
        ProgramCohort cohort = programCohortService.findOrThrow(command.cohortId());
        programEnrollmentService.validateAdmissionWindow(cohort);

        applicationRepository
                .findByStudentIdAndProgramIdAndStatus(student.getId(), command.programId(), ProgramEnrollmentApplicationStatus.PENDING)
                .ifPresent(existing -> {
                    throw new AlreadyHasPendingApplicationException(student.getId(), command.programId());
                });

        if (applicationRepository.existsApprovedWithUnpaidAdmissionInvoice(student.getId(), command.programId())) {
            throw new AdmissionFeeAwaitingPaymentException(student.getId(), command.programId());
        }

        requireDni(student);

        ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
        application.setStudent(student);
        application.setProgram(program);
        application.setProgramCohort(cohort);
        application.setStatus(ProgramEnrollmentApplicationStatus.PENDING);
        application.setRequestedAt(LocalDateTime.now(clock));
        return applicationRepository.save(application);
    }
}
