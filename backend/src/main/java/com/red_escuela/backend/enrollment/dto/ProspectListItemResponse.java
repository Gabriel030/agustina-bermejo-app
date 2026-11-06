package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.identity.api.StudentOnboardingSummary;

import java.time.LocalDateTime;
import java.util.List;

public record ProspectListItemResponse(
        Long id,
        Long personId,
        String nombre,
        String apellido,
        String email,
        String telefono,
        Boolean emailVerified,
        LocalDateTime registeredAt,
        List<DocumentType> missingDocuments) {

    public static ProspectListItemResponse of(StudentOnboardingSummary prospect, List<DocumentType> missingDocuments) {
        return new ProspectListItemResponse(prospect.studentId(), prospect.personId(), prospect.firstName(),
                prospect.lastName(), prospect.email(), prospect.phoneNumber(), prospect.emailVerified(),
                prospect.registeredAt(), missingDocuments);
    }
}
