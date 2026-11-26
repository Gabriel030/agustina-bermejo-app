package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class InformativeSessionNotFoundException extends NotFoundException {
    public InformativeSessionNotFoundException(Long id) {
        super("No existe la charla informativa " + id);
    }
}
