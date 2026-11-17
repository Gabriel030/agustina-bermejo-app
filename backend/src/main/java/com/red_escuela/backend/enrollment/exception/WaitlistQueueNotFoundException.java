package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class WaitlistQueueNotFoundException extends NotFoundException {
    public WaitlistQueueNotFoundException(String kind, Long id) {
        super("Lista de espera no encontrada: " + kind + " " + id);
    }
}
