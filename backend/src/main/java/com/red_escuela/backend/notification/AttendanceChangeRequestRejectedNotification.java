package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;

import java.time.LocalDate;
import java.util.List;

public record AttendanceChangeRequestRejectedNotification(Long requestId, Person requestedBy, String studentFullName, LocalDate classDate, String previousStatusLabel, String requestedStatusLabel) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.ATTENDANCE_CHANGE_REQUEST_REJECTED;
    }

    @Override
    public String message() {
        return AttendanceChangeRequestMessage.of(studentFullName, classDate, previousStatusLabel, requestedStatusLabel, "rechazada");
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
        return requestId;
    }
}
