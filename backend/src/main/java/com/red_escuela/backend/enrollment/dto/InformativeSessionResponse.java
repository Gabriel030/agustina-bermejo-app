package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.InformativeSession;
import com.red_escuela.backend.enrollment.entity.InformativeSessionModality;
import com.red_escuela.backend.enrollment.entity.InformativeSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record InformativeSessionResponse(
        Long id,
        String title,
        LocalDateTime scheduledAt,
        InformativeSessionModality modality,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String meetingUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String location,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer capacity,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long relatedProgramId,
        InformativeSessionStatus status,
        long registrationCount,
        boolean registeredByMe) {

    public static InformativeSessionResponse from(InformativeSession session, long registrationCount, boolean registeredByMe) {
        return new InformativeSessionResponse(session.getId(), session.getTitle(), session.getScheduledAt(), session.getModality(),
                session.getMeetingUrl(), session.getLocation(), session.getCapacity(), session.getRelatedProgramId(),
                session.getStatus(), registrationCount, registeredByMe);
    }
}
