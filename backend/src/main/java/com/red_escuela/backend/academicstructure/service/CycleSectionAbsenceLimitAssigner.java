package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;

@Service
public class CycleSectionAbsenceLimitAssigner {

    public void assignToSubjectSection(SubjectSection section, Percentage maxAbsencePercent) {
        section.setMaxAbsenceRate(maxAbsencePercent);
    }

    public void assignToCourseSection(CourseSubjectSection section, Percentage maxAbsencePercent) {
        section.setMaxAbsenceRate(maxAbsencePercent);
    }
}
