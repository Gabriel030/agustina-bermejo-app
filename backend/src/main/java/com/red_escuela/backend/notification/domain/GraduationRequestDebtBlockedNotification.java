package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.domain.Money;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public record GraduationRequestDebtBlockedNotification(Long requestId, Person student, Money totalDue, int installmentCount,
                                                       boolean paymentLinkAvailable) implements DomainNotificationEvent {

    static final String PAYMENTS_LINK = "/alumno/pagos";

    @Override
    public NotificationType type() {
        return NotificationType.GRADUATION_REQUEST_DEBT_BLOCKED;
    }

    @Override
    public String message() {
        String base = "Tu solicitud de egreso está detenida por una deuda pendiente de $" + formattedAmount() + " (" + installmentCount
                + (installmentCount == 1 ? " cuota)." : " cuotas).");
        return paymentLinkAvailable
                ? base + " Regularizala desde Mis pagos: " + PAYMENTS_LINK
                : base + " Acercate a administración para regularizar tu situación.";
    }

    private String formattedAmount() {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(totalDue.amount());
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
        return requestId;
    }
}
