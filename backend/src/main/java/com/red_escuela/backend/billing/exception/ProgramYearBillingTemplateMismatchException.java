package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ProgramYearBillingTemplateMismatchException extends ConflictException {
    public ProgramYearBillingTemplateMismatchException(Long templateId) {
        super("Program year billing template " + templateId + " does not belong to this enrollment's program");
    }
}
