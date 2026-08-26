package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;

public record StudentResponse(
        Long id, Long personId, String nombre, String apellido, String dni,
        String username, String email, StudentOnboardingStatus onboardingStatus,
        String status) {

    public static StudentResponse from(Student student) {
        var person = student.getPerson();
        return new StudentResponse(
                student.getId(),
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                person.getUsername(),
                person.getEmail(),
                student.getOnboardingStatus(),
                person.getStatus() == null ? null : person.getStatus().name());
    }
}
