package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.DocumentType;

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
        String detalleBaja) {

    public static StudentRecordResponse from(Student student) {
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
                student.getWithdrawalReason() == null ? null : student.getWithdrawalReason().name(),
                student.getWithdrawalDetail());
    }
}
