package com.red_escuela.backend.billing.invoice.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class DueDateBeforeIssueDateException extends ConflictException {

    public DueDateBeforeIssueDateException() {
        super("The due date cannot be before the issue date");
    }
}
