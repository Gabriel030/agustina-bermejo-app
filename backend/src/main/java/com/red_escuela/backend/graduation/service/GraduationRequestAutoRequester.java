package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.graduation.exception.GraduationRequestAlreadyActiveException;
import com.red_escuela.backend.graduation.usecase.graduationrequest.RequestGraduationUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationRequestAutoRequester {

    private final ProgramEnrollmentService programEnrollmentService;
    private final RequestGraduationUseCase requestGraduationUseCase;

    public GraduationRequestAutoRequester(ProgramEnrollmentService programEnrollmentService,
                                           RequestGraduationUseCase requestGraduationUseCase) {
        this.programEnrollmentService = programEnrollmentService;
        this.requestGraduationUseCase = requestGraduationUseCase;
    }

    @Transactional
    public void createIfEligible(Long programEnrollmentId) {
        if (!programEnrollmentService.calculateProgress(programEnrollmentId).elegibleParaCompletar()) {
            return;
        }
        try {
            requestGraduationUseCase.request(programEnrollmentId);
        } catch (GraduationRequestAlreadyActiveException e) {
        }
    }
}
