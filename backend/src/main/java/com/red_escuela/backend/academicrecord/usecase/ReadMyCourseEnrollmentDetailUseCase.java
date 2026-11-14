package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.MyCourseEnrollmentDetailResponse;

public interface ReadMyCourseEnrollmentDetailUseCase {
    MyCourseEnrollmentDetailResponse read(Long studentId, Long courseEnrollmentId);
}
