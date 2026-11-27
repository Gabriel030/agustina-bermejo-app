package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@Transactional(readOnly = true)
public class CourseSectionTermEndResolver {

    public LocalDate termEndDate(CourseSubjectSection section) {
        return termOf(section).getEndDate().toLocalDate();
    }

    public LocalDate termStartDate(CourseSubjectSection section) {
        return termOf(section).getStartDate().toLocalDate();
    }

    private Term termOf(CourseSubjectSection section) {
        AcademicOfferingCycle cycle = section.getAcademicOfferingCycle();
        return cycle != null ? cycle.getTerm() : section.getTerm();
    }
}
