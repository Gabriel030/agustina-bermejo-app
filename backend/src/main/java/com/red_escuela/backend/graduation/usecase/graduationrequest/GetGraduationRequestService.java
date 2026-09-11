package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetGraduationRequestService implements GetGraduationRequestUseCase {

    private final GraduationRequestFinder graduationRequestFinder;

    public GetGraduationRequestService(GraduationRequestFinder graduationRequestFinder) {
        this.graduationRequestFinder = graduationRequestFinder;
    }

    @Override
    public GraduationRequest get(Long id) {
        return graduationRequestFinder.findOrThrow(id);
    }
}
