package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.GraduationRequest;
import com.red_escuela.backend.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;

import java.util.List;

public record GraduationRequestApprovedEvent(GraduationRequest request, Person student) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.GRADUATION_REQUEST_APPROVED;
    }

    @Override
    public String message() {
        return "Tu trámite de egreso fue aprobado: " + request.getProgramEnrollment().getProgram().getName();
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student));
    }

    @Override
    public String relatedEntityType() {
        return "GraduationRequest";
    }

    @Override
    public Long relatedEntityId() {
        return request.getId();
    }
}
