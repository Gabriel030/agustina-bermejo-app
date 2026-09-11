package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.notification.GraduationRequestApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class ApproveGraduationRequestService implements ApproveGraduationRequestUseCase {

    private final GraduationRequestRepository graduationRequestRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final GraduationRequestFinder graduationRequestFinder;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveGraduationRequestService(GraduationRequestRepository graduationRequestRepository,
                                            ProgramEnrollmentRepository programEnrollmentRepository,
                                            GraduationRequestFinder graduationRequestFinder,
                                            CurrentPersonService currentPersonService,
                                            ApplicationEventPublisher eventPublisher) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.graduationRequestFinder = graduationRequestFinder;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public GraduationRequest approve(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        if (request.getStatus() != GraduationRequestStatus.ADMIN_VERIFIED) {
            throw new InvalidGraduationRequestStateException(graduationRequestId);
        }

        Person decidedBy = currentPersonService.resolveCurrentPerson();
        request.setStatus(GraduationRequestStatus.APPROVED);
        request.setDecidedBy(decidedBy);
        request.setDecidedAt(LocalDateTime.now());
        graduationRequestRepository.save(request);

        ProgramEnrollment programEnrollment = request.getProgramEnrollment();
        programEnrollment.setStatus(ProgramEnrollmentStatus.COMPLETED);
        programEnrollment.setCompletionDate(LocalDateTime.now());
        programEnrollmentRepository.save(programEnrollment);

        eventPublisher.publishEvent(new GraduationRequestApprovedEvent(request, programEnrollment.getStudent().getPerson()));
        return request;
    }
}
