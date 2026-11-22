package com.red_escuela.backend.enrollment.usecase.curriculumprogress;

import com.red_escuela.backend.enrollment.dto.CurriculumProgressResponse;
import com.red_escuela.backend.enrollment.service.CurriculumProgressReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReadOwnCurriculumProgressService implements ReadOwnCurriculumProgressUseCase {

    private final CurriculumProgressReader curriculumProgressReader;

    public ReadOwnCurriculumProgressService(CurriculumProgressReader curriculumProgressReader) {
        this.curriculumProgressReader = curriculumProgressReader;
    }

    @Override
    public CurriculumProgressResponse read(Long studentId, Long programEnrollmentId) {
        return CurriculumProgressResponse.from(curriculumProgressReader.readOwnProgress(studentId, programEnrollmentId));
    }
}
