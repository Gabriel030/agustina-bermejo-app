package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.specialist.GraduationRequestRegistrar;
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
