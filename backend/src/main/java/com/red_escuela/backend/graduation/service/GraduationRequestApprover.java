package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentGraduationRecorder;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.exception.GraduationRequirementsNotMetException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class GraduationRequestApprover {

    private final GraduationEligibilityChecker graduationEligibilityChecker;
    private final ProgramEnrollmentGraduationRecorder programEnrollmentGraduationRecorder;
    private final GraduationRequestRepository graduationRequestRepository;

    public GraduationRequestApprover(GraduationEligibilityChecker graduationEligibilityChecker,
                                     ProgramEnrollmentGraduationRecorder programEnrollmentGraduationRecorder,
                                     GraduationRequestRepository graduationRequestRepository) {
        this.graduationEligibilityChecker = graduationEligibilityChecker;
        this.programEnrollmentGraduationRecorder = programEnrollmentGraduationRecorder;
        this.graduationRequestRepository = graduationRequestRepository;
    }

    public Long requireApprovableAndFindStudentId(GraduationRequest request) {
        request.requireAwaitingDirectorDecision();
        request.getProgramEnrollment().requireActive();
        Long programEnrollmentId = request.getProgramEnrollment().getId();
        if (!graduationEligibilityChecker.isEligibleToGraduate(programEnrollmentId)) {
            throw new GraduationRequirementsNotMetException(programEnrollmentId);
        }
        return request.getProgramEnrollment().getStudent().getId();
    }

    public void recordApproval(GraduationRequest request, Person director, LocalDateTime approvedAt) {
        request.approve(director, approvedAt);
        graduationRequestRepository.save(request);
        programEnrollmentGraduationRecorder.recordGraduation(request.getProgramEnrollment());
    }
}
