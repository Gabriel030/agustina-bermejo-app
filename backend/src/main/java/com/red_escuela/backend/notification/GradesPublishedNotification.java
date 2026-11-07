package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record GradesPublishedNotification(Long sectionId, String sectionName, String gradeTypeLabel, int sequenceNumber,
                                          List<Person> students) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.GRADES_PUBLISHED;
    }

    @Override
    public String message() {
        return "Se publicó tu nota de " + gradeTypeLabel + " " + sequenceNumber + " en " + sectionName;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return students.stream().map(person -> (NotificationRecipient) new PersonRecipient(person)).toList();
    }

    @Override
    public String relatedEntityType() {
        return "Section";
    }

    @Override
    public Long relatedEntityId() {
        return sectionId;
    }
}
