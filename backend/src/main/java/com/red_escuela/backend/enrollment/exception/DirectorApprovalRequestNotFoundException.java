package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class DirectorApprovalRequestNotFoundException extends NotFoundException {
    public DirectorApprovalRequestNotFoundException(Long id) {
        super("No existe la solicitud de excepción del Director " + id);
    }
}
