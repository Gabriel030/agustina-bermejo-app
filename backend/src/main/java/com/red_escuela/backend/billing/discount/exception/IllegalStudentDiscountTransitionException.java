package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountEvent;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.shared.exception.ConflictException;

public class IllegalStudentDiscountTransitionException extends ConflictException {
    public IllegalStudentDiscountTransitionException(StudentDiscountStatus from, StudentDiscountEvent event) {
        super("Student discount in status " + from + " does not accept " + event);
    }
}
