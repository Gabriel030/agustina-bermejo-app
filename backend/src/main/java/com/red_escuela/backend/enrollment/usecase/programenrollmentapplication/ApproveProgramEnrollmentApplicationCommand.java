package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.identity.entity.Person;

public record ApproveProgramEnrollmentApplicationCommand(Long id, Person decidedBy) {
}
