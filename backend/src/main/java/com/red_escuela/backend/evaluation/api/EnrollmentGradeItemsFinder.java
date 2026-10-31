package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class EnrollmentGradeItemsFinder {

    private final GradeItemRepository gradeItemRepository;

    public EnrollmentGradeItemsFinder(GradeItemRepository gradeItemRepository) {
        this.gradeItemRepository = gradeItemRepository;
    }

    public List<EnrollmentGradeItemView> findByProgramEnrollment(Long enrollmentId) {
        return gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId).stream()
                .map(EnrollmentGradeItemView::from)
                .toList();
    }
}
