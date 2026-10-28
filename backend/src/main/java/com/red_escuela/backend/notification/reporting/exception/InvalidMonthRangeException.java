package com.red_escuela.backend.notification.reporting.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.YearMonth;

public class InvalidMonthRangeException extends BadRequestException {

    public InvalidMonthRangeException(YearMonth from, YearMonth to) {
        super("El rango de meses " + from + " a " + to + " no es válido: debe ser creciente y de hasta 36 meses");
    }
}
