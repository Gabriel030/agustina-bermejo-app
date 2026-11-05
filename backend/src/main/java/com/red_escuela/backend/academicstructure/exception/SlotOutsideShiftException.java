package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalTime;

public class SlotOutsideShiftException extends BadRequestException {
    public SlotOutsideShiftException(SectionShift shift, LocalTime start, LocalTime end, LocalTime shiftStart, LocalTime shiftEnd) {
        super("La franja " + start + "-" + end + " queda fuera del turno " + shift + " (" + shiftStart + "-" + shiftEnd + ")");
    }
}
