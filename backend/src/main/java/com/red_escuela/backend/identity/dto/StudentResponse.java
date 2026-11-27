package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.identity.api.StudentAttendanceRisk;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;

import java.time.LocalDateTime;
import java.util.List;

public record StudentResponse(
        Long id, Long personId, String nombre, String apellido, String dni,
        String username, String email, StudentOnboardingStatus onboardingStatus,
        String status, Boolean emailVerified, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime registeredAt, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime withdrawnAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String withdrawalReason, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) boolean hasAvatar,
        StudentAttendanceRisk attendanceRisk, List<String> missingRequiredDocuments) {

    public static StudentResponse from(Student student, boolean includeWithdrawalReason) {
        return from(student, includeWithdrawalReason, StudentAttendanceRisk.NONE, List.of());
    }

    public static StudentResponse from(Student student, boolean includeWithdrawalReason,
                                       StudentAttendanceRisk attendanceRisk, List<String> missingRequiredDocuments) {
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
                person.getAvatarPath() != null,
                attendanceRisk,
                missingRequiredDocuments);
    }
}
