package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.identity.entity.Person;

public record ProgramApplicationApprovedEvent(Long applicationId, Person student, String programName) {
}
