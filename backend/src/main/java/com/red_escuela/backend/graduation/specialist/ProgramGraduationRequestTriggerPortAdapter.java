package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramGraduationRequestTriggerPortAdapter implements GraduationRequestTriggerPort {

    private final GraduationRequestAutoRequester graduationRequestAutoRequester;
    private final GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger;

    public ProgramGraduationRequestTriggerPortAdapter(GraduationRequestAutoRequester graduationRequestAutoRequester,
                                                      GraduationRequestOutdatedFlagger graduationRequestOutdatedFlagger) {
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
    @Transactional
    public void reopenGraduationRequestForReview(Long programEnrollmentId) {
        graduationRequestOutdatedFlagger.reopenAndFlagRequests(programEnrollmentId);
    }
}
