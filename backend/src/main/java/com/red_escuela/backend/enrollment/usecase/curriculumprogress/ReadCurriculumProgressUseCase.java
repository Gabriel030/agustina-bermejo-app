package com.red_escuela.backend.enrollment.usecase.curriculumprogress;

import com.red_escuela.backend.enrollment.dto.CurriculumProgressResponse;

public interface ReadCurriculumProgressUseCase {
    CurriculumProgressResponse read(Long programEnrollmentId);
}
