package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class ProgramYearBillingTemplateNotFoundException extends NotFoundException {
    public ProgramYearBillingTemplateNotFoundException(Long id) {
        super("Program year billing template not found: " + id);
    }
}
