package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class WaitlistReasonRequiredException extends BadRequestException {
    public WaitlistReasonRequiredException() {
        super("Indicá el motivo (entre 10 y 500 caracteres): es obligatorio y queda registrado");
    }
}
