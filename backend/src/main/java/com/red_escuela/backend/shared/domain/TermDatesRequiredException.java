package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class TermDatesRequiredException extends BadRequestException implements ReportsErrorCode {

    public TermDatesRequiredException() {
        super("El período necesita fecha de inicio y de fin");
    }
}
