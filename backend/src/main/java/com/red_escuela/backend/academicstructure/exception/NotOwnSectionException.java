package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public class NotOwnSectionException extends NotFoundException {
    public NotOwnSectionException(Long instructorId, Long sectionId) {
        super("El Docente " + instructorId + " no integra la comisión " + sectionId);
    }
}
