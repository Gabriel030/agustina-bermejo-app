package com.red_escuela.backend.enrollment.usecase.curriculumprogress;

import com.red_escuela.backend.enrollment.dto.GraduationProjectionResponse;
import com.red_escuela.backend.enrollment.service.GraduationProjectionReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReadOwnGraduationProjectionService implements ReadOwnGraduationProjectionUseCase {

    private final GraduationProjectionReader graduationProjectionReader;

    public ReadOwnGraduationProjectionService(GraduationProjectionReader graduationProjectionReader) {
        this.graduationProjectionReader = graduationProjectionReader;
    }

    @Override
    public GraduationProjectionResponse read(Long studentId, Long programEnrollmentId) {
        return GraduationProjectionResponse.from(graduationProjectionReader.readOwnProjection(studentId, programEnrollmentId));
    }
}
