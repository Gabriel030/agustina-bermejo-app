package com.red_escuela.backend.notification;

import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record EnrollmentFeeExemptionGrantedEvent(EnrollmentFeeExemption exemption) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.ENROLLMENT_FEE_EXEMPTION_GRANTED;
    }

    @Override
    public String message() {
        return "La institución te habilitó para inscribirte sin tener la matrícula paga. La matrícula sigue pendiente de pago.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(exemption.getProgramEnrollment().getStudent().getPerson()));
    }

    @Override
    public String relatedEntityType() {
        return "ProgramEnrollment";
    }

    @Override
    public Long relatedEntityId() {
        return exemption.getProgramEnrollment().getId();
    }
}
