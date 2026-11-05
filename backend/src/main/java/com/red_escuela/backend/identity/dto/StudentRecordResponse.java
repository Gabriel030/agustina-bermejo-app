package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.identity.domain.LegalAge;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StudentRecordResponse(
        Long id,
        Long personId,
        String legajo,
        String nombre,
        String apellido,
        String dni,
        DocumentType documentType,
        String username,
        String email,
        String sexo,
        String nacionalidad,
        String telefono,
        StudentOnboardingStatus onboardingStatus,
        String status,
        AddressResponse domicilio,
        String motivoBaja,
        String detalleBaja,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean emailVerified,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate birthDate,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime registeredAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime withdrawnAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean minor,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) GuardianResponse guardian,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) EmergencyContactResponse emergencyContact) {

    public static StudentRecordResponse from(Student student, boolean includeWithdrawalReason) {
        var p = student.getPerson();
        return new StudentRecordResponse(
                student.getId(),
                p.getId(),
                p.getFile(),
                p.getName(),
                p.getLastname(),
                p.getDni(),
                p.getDocumentType(),
                p.getUsername(),
                p.emailValue(),
                p.getSex(),
                p.getNationality(),
                p.getPhoneNumber(),
                student.getOnboardingStatus(),
                p.getStatus() == null ? null : p.getStatus().name(),
                p.getAddress() == null ? null : AddressResponse.from(p.getAddress()),
                includeWithdrawalReason && student.getWithdrawalReason() != null
                        ? student.getWithdrawalReason().name() : null,
                includeWithdrawalReason ? student.getWithdrawalDetail() : null,
                p.getEmailVerified(),
                p.getBirthDate(),
                student.getRegisteredAt(),
                student.getWithdrawnAt(),
                p.getBirthDate() != null && LegalAge.isMinor(p.getBirthDate(), ApplicationTime.today()),
                GuardianResponse.from(student),
                EmergencyContactResponse.from(student));
    }
}
