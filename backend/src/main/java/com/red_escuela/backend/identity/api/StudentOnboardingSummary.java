package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;

public record StudentOnboardingSummary(
        Long studentId,
        Long personId,
        String firstName,
        String lastName,
        String dni,
        String username,
        String email,
        StudentOnboardingStatus onboardingStatus,
        String personStatus,
        Boolean emailVerified) {

    static StudentOnboardingSummary of(Student student) {
        var person = student.getPerson();
        return new StudentOnboardingSummary(
                student.getId(),
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                person.getUsername(),
                person.emailValue(),
                student.getOnboardingStatus(),
                person.getStatus() == null ? null : person.getStatus().name(),
                person.getEmailVerified());
    }
}
