package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ClassSession;
import com.red_escuela.backend.entity.ClassSessionStatus;

public record ClassSessionResponse(String status, String topic, String observations) {

    public static ClassSessionResponse from(ClassSession session) {
        if (session == null) {
            return new ClassSessionResponse(ClassSessionStatus.SCHEDULED.name(), null, null);
        }
        return new ClassSessionResponse(session.getStatus().name(), session.getTopic(), session.getObservations());
    }
}
