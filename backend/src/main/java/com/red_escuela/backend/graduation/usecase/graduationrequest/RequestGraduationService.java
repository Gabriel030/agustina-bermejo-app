package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.service.GraduationRequestRegistrar;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RequestGraduationService implements RequestGraduationUseCase {

    private final GraduationRequestRegistrar graduationRequestRegistrar;

    public RequestGraduationService(GraduationRequestRegistrar graduationRequestRegistrar) {
        this.graduationRequestRegistrar = graduationRequestRegistrar;
    }

    @Override
    public GraduationRequest request(Long programEnrollmentId) {
        return graduationRequestRegistrar.registerRequest(programEnrollmentId);
    }
}
