package com.red_escuela.backend.billing.account.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class CurrentAccountEntrySourceMismatchException extends ConflictException {

    public CurrentAccountEntrySourceMismatchException() {
        super("The entry source does not belong to this student current account");
    }
}
