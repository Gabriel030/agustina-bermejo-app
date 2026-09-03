package com.red_escuela.backend.exception;

public class NotOwnNotificationException extends RuntimeException {
    public NotOwnNotificationException(Long notificationId) {
        super("La notificación " + notificationId + " no pertenece a la persona autenticada");
    }
}
