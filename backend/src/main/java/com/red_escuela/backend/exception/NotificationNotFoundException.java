package com.red_escuela.backend.exception;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(Long id) {
        super("Notificación no encontrada: " + id);
    }
}
