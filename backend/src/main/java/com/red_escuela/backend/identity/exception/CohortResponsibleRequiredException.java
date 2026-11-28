package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CohortResponsibleRequiredException extends BadRequestException {
    public CohortResponsibleRequiredException() {
        super("La cohorte necesita al menos un preceptor responsable");
    }
}
