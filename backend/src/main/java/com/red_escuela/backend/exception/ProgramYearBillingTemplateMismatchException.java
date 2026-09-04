package com.red_escuela.backend.exception;

public class ProgramYearBillingTemplateMismatchException extends RuntimeException {
    public ProgramYearBillingTemplateMismatchException(Long templateId) {
        super("Program year billing template " + templateId + " does not belong to this enrollment's program");
    }
}
