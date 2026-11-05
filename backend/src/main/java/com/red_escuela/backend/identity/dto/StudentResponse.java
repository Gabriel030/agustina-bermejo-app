package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;

import java.time.LocalDateTime;

public record StudentResponse(
        Long id, Long personId, String nombre, String apellido, String dni,
        String username, String email, StudentOnboardingStatus onboardingStatus,
        String status, Boolean emailVerified, LocalDateTime registeredAt, LocalDateTime withdrawnAt,
        String withdrawalReason, boolean hasAvatar) {

    public static StudentResponse from(Student student, boolean includeWithdrawalReason) {
        var person = student.getPerson();
        return new StudentResponse(
                student.getId(),
                person.getId(),
                person.getName(),
                person.getLastname(),
                person.getDni(),
                person.getUsername(),
                person.emailValue(),
                student.getOnboardingStatus(),
                person.getStatus() == null ? null : person.getStatus().name(),
                person.getEmailVerified(),
                student.getRegisteredAt(),
                student.getWithdrawnAt(),
                includeWithdrawalReason && student.getWithdrawalReason() != null
                        ? student.getWithdrawalReason().name() : null,
                person.getAvatarPath() != null);
    }
}
