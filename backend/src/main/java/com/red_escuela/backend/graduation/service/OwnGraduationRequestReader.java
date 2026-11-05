package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.enrollment.api.ElectiveGroupProgressView;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
import com.red_escuela.backend.enrollment.api.ProgramProgressView;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.dto.OwnGraduationRequestDetail;
import com.red_escuela.backend.graduation.dto.OwnGraduationRequestResponse;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnGraduationRequestReader {

    private final GraduationRequestRepository graduationRequestRepository;
    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final ProgramProgressReader programProgressReader;

    public OwnGraduationRequestReader(GraduationRequestRepository graduationRequestRepository,
                                      ProgramEnrollmentFinder programEnrollmentFinder,
                                      ProgramProgressReader programProgressReader) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.programProgressReader = programProgressReader;
    }

    @Transactional(readOnly = true)
    public OwnGraduationRequestResponse readFor(Long programEnrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentFinder.find(programEnrollmentId);
        ProgramProgressView progress = programProgressReader.calculateProgress(programEnrollmentId);
        OwnGraduationRequestDetail request = graduationRequestRepository
                .findFirstByProgramEnrollmentIdOrderByIdDesc(programEnrollmentId)
                .map(OwnGraduationRequestDetail::from).orElse(null);
        return new OwnGraduationRequestResponse(
                enrollment.getId(), enrollment.getProgram().getName(), enrollment.getStatus() == ProgramEnrollmentStatus.COMPLETED,
                progress.eligibleToComplete(), progress.accumulatedCredits(), progress.requiredCredits(),
                progress.pendingMandatorySubjects(),
                progress.electiveGroups().stream().filter(group -> !group.fulfilled()).map(ElectiveGroupProgressView::electiveGroupName).toList(),
                request);
    }
}
