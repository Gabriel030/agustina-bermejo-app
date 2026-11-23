package com.red_escuela.backend.graduation.certificate.examregistration.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class InvalidExamRegistrationRecipientException extends BadRequestException implements ReportsErrorCode {

    public InvalidExamRegistrationRecipientException(String message) {
        super(message);
    }
}
