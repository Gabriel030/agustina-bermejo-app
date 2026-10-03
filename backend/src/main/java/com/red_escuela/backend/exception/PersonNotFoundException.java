package com.red_escuela.backend.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class PersonNotFoundException extends NotFoundException {
    public PersonNotFoundException(Long id) {
        super("Person no encontrada: " + id);
    }
}
