package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.identity.entity.Student;

public record RequestProgramEnrollmentApplicationCommand(Student student, Long programId, Long cohortId) {
}
