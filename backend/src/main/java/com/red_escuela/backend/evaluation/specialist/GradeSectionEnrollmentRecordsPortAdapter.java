package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.enrollment.api.SectionEnrollmentRecordsPort;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class GradeSectionEnrollmentRecordsPortAdapter implements SectionEnrollmentRecordsPort {

    private final GradeItemRepository gradeItemRepository;

    public GradeSectionEnrollmentRecordsPortAdapter(GradeItemRepository gradeItemRepository) {
        this.gradeItemRepository = gradeItemRepository;
    }

    @Override
    public boolean hasProgramEnrollmentRecords(Long programSectionEnrollmentId) {
        return gradeItemRepository.existsByProgramSubjectSectionEnrollmentId(programSectionEnrollmentId);
    }
}
