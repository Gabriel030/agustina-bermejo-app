package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularEnrollmentNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class RegularEnrollmentFactsFinder {

    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final CourseEnrollmentFinder courseEnrollmentFinder;

    public RegularEnrollmentFactsFinder(ProgramEnrollmentFinder programEnrollmentFinder, CourseEnrollmentFinder courseEnrollmentFinder) {
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.courseEnrollmentFinder = courseEnrollmentFinder;
    }

    public RegularEnrollmentFacts findOwnedBy(RegularityKind kind, Long enrollmentId, Long studentId) {
        RegularEnrollmentFacts facts = find(kind, enrollmentId);
        if (!facts.student().getId().equals(studentId)) {
            throw new RegularEnrollmentNotFoundException();
        }
        return facts;
    }

    public RegularEnrollmentFacts find(RegularityKind kind, Long enrollmentId) {
        if (kind == RegularityKind.PROGRAM) {
            ProgramEnrollment enrollment = programEnrollmentFinder.requireById(enrollmentId);
            return new RegularEnrollmentFacts(enrollment.getStudent(), "la carrera", enrollment.getProgram().getName());
        }
        CourseEnrollment enrollment = courseEnrollmentFinder.requireById(enrollmentId);
        return new RegularEnrollmentFacts(enrollment.getStudent(), "el curso", enrollment.getCourse().getName());
    }
}
