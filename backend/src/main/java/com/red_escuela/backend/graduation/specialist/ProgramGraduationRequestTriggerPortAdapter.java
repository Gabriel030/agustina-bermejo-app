package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramGraduationRequestTriggerPortAdapter implements GraduationRequestTriggerPort {

    private final GraduationRequestAutoRequester graduationRequestAutoRequester;
    private final GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger;
    private final GraduationRequestRepository graduationRequestRepository;
    private final ProgramEnrollmentFinder programEnrollmentFinder;

    public ProgramGraduationRequestTriggerPortAdapter(GraduationRequestAutoRequester graduationRequestAutoRequester,
                                                      GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger,
                                                      GraduationRequestRepository graduationRequestRepository,
                                                      ProgramEnrollmentFinder programEnrollmentFinder) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.graduationRequestAutoRequester = graduationRequestAutoRequester;
        this.graduationRequestOutdatedFlagger = graduationRequestOutdatedFlagger;
    }

    @Override
    @Transactional
    public void requestGraduationIfEligible(Long programEnrollmentId) {
        graduationRequestAutoRequester.createIfEligible(programEnrollmentId);
    }

    @Override
    @Transactional
    public void flagGraduationRequestOutdated(Long programEnrollmentId) {
        graduationRequestOutdatedFlagger.flagOutdatedRequests(programEnrollmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isGraduationFinalized(Long programEnrollmentId) {
        return graduationRequestRepository.existsByProgramEnrollmentIdAndStatus(programEnrollmentId, GraduationRequestStatus.APPROVED)
                || !programEnrollmentFinder.find(programEnrollmentId).isActive();
    }
}
