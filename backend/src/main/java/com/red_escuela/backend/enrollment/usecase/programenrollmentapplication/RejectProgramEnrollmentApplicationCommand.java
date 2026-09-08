package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.identity.entity.Person;

public record RejectProgramEnrollmentApplicationCommand(Long id, Person decidedBy, String reason) {
}
