package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramGraduationRequestTriggerPortAdapter implements GraduationRequestTriggerPort {

    private final GraduationRequestAutoRequester graduationRequestAutoRequester;

    public ProgramGraduationRequestTriggerPortAdapter(GraduationRequestAutoRequester graduationRequestAutoRequester) {
        this.graduationRequestAutoRequester = graduationRequestAutoRequester;
    }

    @Override
    @Transactional
    public void requestGraduationIfEligible(Long programEnrollmentId) {
        graduationRequestAutoRequester.createIfEligible(programEnrollmentId);
    }
}
