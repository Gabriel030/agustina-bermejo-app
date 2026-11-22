package com.red_escuela.backend.enrollment.usecase.curriculumprogress;

import com.red_escuela.backend.enrollment.dto.CurriculumProgressResponse;

public interface ReadOwnCurriculumProgressUseCase {
    CurriculumProgressResponse read(Long studentId, Long programEnrollmentId);
}
