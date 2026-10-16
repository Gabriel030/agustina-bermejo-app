package com.red_escuela.backend.billing.discount.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class StudentDiscountNotFoundException extends NotFoundException {
    public StudentDiscountNotFoundException(Long studentDiscountId) {
        super("No existe el descuento " + studentDiscountId + ".");
    }
}
