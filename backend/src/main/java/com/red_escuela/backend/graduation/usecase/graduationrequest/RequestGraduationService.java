package com.red_escuela.backend.graduation.usecase.graduationrequest;

import java.time.Clock;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.GraduationRequestAlreadyActiveException;
import com.red_escuela.backend.graduation.exception.NotEligibleForGraduationException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@UseCase
public class RequestGraduationService implements RequestGraduationUseCase {

    private final GraduationRequestRepository graduationRequestRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final Clock clock;

    public RequestGraduationService(GraduationRequestRepository graduationRequestRepository,
                                     ProgramEnrollmentRepository programEnrollmentRepository,
                                     ProgramEnrollmentService programEnrollmentService,
                                     Clock clock) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public GraduationRequest request(Long programEnrollmentId) {
        ProgramEnrollment programEnrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        if (!programEnrollmentService.calculateProgress(programEnrollmentId).elegibleParaCompletar()) {
            throw new NotEligibleForGraduationException(programEnrollmentId);
        }

        List<GraduationRequest> activos = graduationRequestRepository
                .findByProgramEnrollmentIdAndStatusNot(programEnrollmentId, GraduationRequestStatus.REJECTED);
        if (!activos.isEmpty()) {
            throw new GraduationRequestAlreadyActiveException(programEnrollmentId);
        }

        GraduationRequest request = new GraduationRequest();
        request.setProgramEnrollment(programEnrollment);
        request.setStatus(GraduationRequestStatus.REQUESTED);
        request.setRequestedAt(LocalDateTime.now(clock));
        try {
            return graduationRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new GraduationRequestAlreadyActiveException(programEnrollmentId);
        }
    }
}
