package com.red_escuela.backend.billing.exception;

public class StudentInDebtException extends RuntimeException {
    public StudentInDebtException(Long studentId) {
        super("Student " + studentId + " has an overdue invoice in some enrollment — cannot enroll in anything new until it's resolved");
    }
}
