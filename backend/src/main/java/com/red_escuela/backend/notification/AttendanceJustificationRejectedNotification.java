package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record AttendanceJustificationRejectedNotification(Long attendanceId, Person student, LocalDate classDate, String subjectName, String rejectionReason) implements DomainNotificationEvent {
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public NotificationType type() {
        return NotificationType.ATTENDANCE_JUSTIFICATION_REJECTED;
    }

    @Override
    public String message() {
        return "Tu justificación de la inasistencia del " + classDate.format(DAY_FORMAT) + " en " + subjectName + " fue rechazada. Motivo: " + rejectionReason;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student));
    }

    @Override
    public String relatedEntityType() {
        return "Attendance";
    }

    @Override
    public Long relatedEntityId() {
        return attendanceId;
    }
}
