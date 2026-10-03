package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class StudentInDebtException extends ConflictException {
    public StudentInDebtException(Long studentId) {
        super("Student " + studentId + " has an overdue invoice in some enrollment — cannot enroll in anything new until it's resolved");
    }
}
