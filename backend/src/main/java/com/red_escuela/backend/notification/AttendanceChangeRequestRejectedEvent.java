package com.red_escuela.backend.notification;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;

import java.util.List;

public record AttendanceChangeRequestRejectedEvent(AttendanceChangeRequest request, Person requestedBy) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.ATTENDANCE_CHANGE_REQUEST_REJECTED;
    }

    @Override
    public String message() {
        return "Tu solicitud de modificación de asistencia fue rechazada: "
                + request.getPreviousStatus() + " → " + request.getRequestedStatus();
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(requestedBy));
    }

    @Override
    public String relatedEntityType() {
        return "AttendanceChangeRequest";
    }

    @Override
    public Long relatedEntityId() {
        return request.getId();
    }
}
