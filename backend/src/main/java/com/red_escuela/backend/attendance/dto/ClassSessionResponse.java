package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;

public record ClassSessionResponse(String status, String topic, String observations) {

    public static ClassSessionResponse from(ClassSession session) {
        if (session == null) {
            return new ClassSessionResponse(ClassSessionStatus.SCHEDULED.name(), null, null);
        }
        return new ClassSessionResponse(session.getStatus().name(), session.getTopic(), session.getObservations());
    }
}
