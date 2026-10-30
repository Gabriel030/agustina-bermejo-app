package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.service.GraduationRequestRegistrar;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RequestGraduationService implements RequestGraduationUseCase {

    private final GraduationRequestRegistrar graduationRequestRegistrar;

    public RequestGraduationService(GraduationRequestRegistrar graduationRequestRegistrar) {
        this.graduationRequestRegistrar = graduationRequestRegistrar;
    }

    @Override
    @Transactional
    public GraduationRequest request(Long programEnrollmentId) {
        return graduationRequestRegistrar.registerRequest(programEnrollmentId);
    }
}
