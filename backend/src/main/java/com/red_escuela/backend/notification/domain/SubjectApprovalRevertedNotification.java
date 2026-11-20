package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record SubjectApprovalRevertedNotification(String subjectName, List<String> missingPrerequisiteNames,
                                                  Person student) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SUBJECT_APPROVAL_REVERTED;
    }

    @Override
    public String message() {
        String missing = missingPrerequisiteNames.isEmpty() ? "una correlativa" : String.join(", ", missingPrerequisiteNames);
        return "Tu materia " + subjectName + " volvió a estar regular porque ya no tenés aprobada la correlativa " + missing
                + ". Volvé a aprobarla para recuperar la aprobación.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student));
    }

    @Override
    public String relatedEntityType() {
        return null;
    }

    @Override
    public Long relatedEntityId() {
        return null;
    }
}
