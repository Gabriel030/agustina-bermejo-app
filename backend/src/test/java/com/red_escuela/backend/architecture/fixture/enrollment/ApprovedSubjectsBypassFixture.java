package com.red_escuela.backend.architecture.fixture.enrollment;

import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;

import java.util.List;

public class ApprovedSubjectsBypassFixture {

    public List<SubjectSectionEnrollmentRepository.CurriculumProgressRow> readApprovedRowsDirectly(
            SubjectSectionEnrollmentRepository repository, Long programEnrollmentId) {
        return repository.findCurriculumProgressRows(programEnrollmentId);
    }
}
