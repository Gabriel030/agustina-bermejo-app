package com.red_escuela.backend.notification.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class NotOwnNotificationException extends ForbiddenException {
    public NotOwnNotificationException(Long notificationId) {
        super("La notificación " + notificationId + " no pertenece a la persona autenticada");
    }
}
