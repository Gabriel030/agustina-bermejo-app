package com.red_escuela.backend.enrollment.usecase.curriculumprogress;

import com.red_escuela.backend.enrollment.dto.GraduationProjectionResponse;

public interface ReadGraduationProjectionUseCase {
    GraduationProjectionResponse read(Long programEnrollmentId);
}
