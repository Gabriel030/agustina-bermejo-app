package com.red_escuela.backend.notification.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class NotificationNotFoundException extends NotFoundException {
    public NotificationNotFoundException(Long id) {
        super("Notificación no encontrada: " + id);
    }
}
