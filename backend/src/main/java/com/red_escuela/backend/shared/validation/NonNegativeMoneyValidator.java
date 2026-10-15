package com.red_escuela.backend.shared.validation;

import com.red_escuela.backend.shared.domain.Money;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NonNegativeMoneyValidator implements ConstraintValidator<NonNegativeMoney, Money> {

    @Override
    public boolean isValid(Money money, ConstraintValidatorContext context) {
        return money == null || !money.isNegative();
    }
}
