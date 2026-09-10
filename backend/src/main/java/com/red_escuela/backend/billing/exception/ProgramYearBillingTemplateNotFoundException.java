package com.red_escuela.backend.billing.exception;

public class ProgramYearBillingTemplateNotFoundException extends RuntimeException {
    public ProgramYearBillingTemplateNotFoundException(Long id) {
        super("Program year billing template not found: " + id);
    }
}
