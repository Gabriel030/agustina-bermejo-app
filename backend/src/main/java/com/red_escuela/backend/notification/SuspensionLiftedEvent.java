package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.BillableEnrollment;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.NotificationType;
import com.red_escuela.backend.entity.ProgramEnrollment;

import java.time.YearMonth;
import java.util.List;

public record SuspensionLiftedEvent(BillableEnrollment billable, YearMonth suspendedSince, YearMonth suspendedUntil)
        implements DomainNotificationEvent {

    @Override
    public NotificationType type() {
        return NotificationType.SUSPENSION_LIFTED;
    }

    @Override
    public String message() {
        return "Tu suspensión por mora se levantó — ya estás al día.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(billable.getStudent().getPerson()));
    }

    @Override
    public String relatedEntityType() {
        return switch (billable) {
            case CourseEnrollment ignored -> "CourseEnrollment";
            case ProgramEnrollment ignored -> "ProgramEnrollment";
            default -> "BillableEnrollment";
        };
    }

    @Override
    public Long relatedEntityId() {
        return switch (billable) {
            case CourseEnrollment ce -> ce.getId();
            case ProgramEnrollment pe -> pe.getId();
            default -> null;
        };
    }
}
