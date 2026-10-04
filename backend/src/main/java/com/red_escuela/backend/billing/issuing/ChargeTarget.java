package com.red_escuela.backend.billing.issuing;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

public sealed interface ChargeTarget {
    record ProgramStudent(ProgramEnrollment enrollment) implements ChargeTarget {
    }

    record CourseStudent(CourseEnrollment enrollment) implements ChargeTarget {
    }

    record ProgramApplicant(Program program, ProgramCohort cohort) implements ChargeTarget {
    }

    record CourseApplicant(Course course) implements ChargeTarget {
    }
}
