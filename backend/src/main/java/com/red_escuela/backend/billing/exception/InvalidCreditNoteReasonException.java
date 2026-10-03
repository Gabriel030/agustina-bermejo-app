package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import com.red_escuela.backend.billing.entity.CreditNoteReasonType;

public class InvalidCreditNoteReasonException extends BadRequestException {
    public InvalidCreditNoteReasonException(CreditNoteReasonType reasonType) {
        super("CreditNoteReasonType " + reasonType + " cannot be requested manually, it is system-generated only");
    }
}
