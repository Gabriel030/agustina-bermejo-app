package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.dto.OwnGraduationRequestResponse;
import com.red_escuela.backend.graduation.specialist.OwnGraduationRequestReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetOwnGraduationRequestService implements GetOwnGraduationRequestUseCase {

    private final OwnGraduationRequestReader ownGraduationRequestReader;

    public GetOwnGraduationRequestService(OwnGraduationRequestReader ownGraduationRequestReader) {
        this.ownGraduationRequestReader = ownGraduationRequestReader;
    }

    @Override
    public OwnGraduationRequestResponse get(Long programEnrollmentId) {
        return ownGraduationRequestReader.readFor(programEnrollmentId);
    }
}
