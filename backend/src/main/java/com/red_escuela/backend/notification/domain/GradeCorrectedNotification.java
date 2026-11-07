package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record GradeCorrectedNotification(Long gradeItemId, String sectionName, String gradeTypeLabel, int sequenceNumber,
                                         Person student) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.GRADE_CORRECTED;
    }

    @Override
    public String message() {
        return "Se corrigió tu nota de " + gradeTypeLabel + " " + sequenceNumber + " en " + sectionName;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student));
    }

    @Override
    public String relatedEntityType() {
        return "GradeItem";
    }

    @Override
    public Long relatedEntityId() {
        return gradeItemId;
    }
}
