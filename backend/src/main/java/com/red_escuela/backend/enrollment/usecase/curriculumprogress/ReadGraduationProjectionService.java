package com.red_escuela.backend.enrollment.usecase.curriculumprogress;

import com.red_escuela.backend.enrollment.dto.GraduationProjectionResponse;
import com.red_escuela.backend.enrollment.service.GraduationProjectionReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReadGraduationProjectionService implements ReadGraduationProjectionUseCase {

    private final GraduationProjectionReader graduationProjectionReader;

    public ReadGraduationProjectionService(GraduationProjectionReader graduationProjectionReader) {
        this.graduationProjectionReader = graduationProjectionReader;
    }

    @Override
    public GraduationProjectionResponse read(Long programEnrollmentId) {
        return GraduationProjectionResponse.from(graduationProjectionReader.readProjection(programEnrollmentId));
    }
}
