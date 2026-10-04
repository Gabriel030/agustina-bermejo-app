package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.identity.entity.Person;

import java.time.LocalDateTime;

public record EnrollmentFeeExemptionResponse(Long id, Long templateId, Integer slot, String reason, String grantedByName, LocalDateTime grantedAt,
                                             String revokedByName, LocalDateTime revokedAt, String revocationNotes, boolean active) {

    public static EnrollmentFeeExemptionResponse from(EnrollmentFeeExemption exemption) {
        return new EnrollmentFeeExemptionResponse(exemption.getId(), exemption.getTemplate().getId(), exemption.getSlot(), exemption.getReason(),
                name(exemption.getGrantedBy()), exemption.getGrantedAt(), name(exemption.getRevokedBy()), exemption.getRevokedAt(),
                exemption.getRevocationNotes(), exemption.isActive());
    }

    private static String name(Person person) {
        return person == null ? null : person.getName() + " " + person.getLastname();
    }
}
