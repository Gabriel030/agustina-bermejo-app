package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

public sealed interface InvoiceRecipient {
    record ProgramEnrollmentRecipient(ProgramEnrollment enrollment) implements InvoiceRecipient {
    }

    record CourseEnrollmentRecipient(CourseEnrollment enrollment) implements InvoiceRecipient {
    }

    record ProgramApplicantRecipient(Program program, ProgramCohort cohort) implements InvoiceRecipient {
    }

    record CourseApplicantRecipient(Course course) implements InvoiceRecipient {
    }
}
