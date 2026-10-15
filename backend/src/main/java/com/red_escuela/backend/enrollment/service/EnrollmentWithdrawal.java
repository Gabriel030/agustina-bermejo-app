package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.api.WithdrawalCause;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record EnrollmentWithdrawal(LocalDate informedDate, Long registeredByPersonId, WithdrawalCause cause) {

    public EnrollmentWithdrawal {
        Objects.requireNonNull(cause, "cause");
    }

    public static EnrollmentWithdrawal voluntaryRegisteredBy(LocalDate informedDate, Long personId) {
        return new EnrollmentWithdrawal(informedDate, Objects.requireNonNull(personId, "personId"), WithdrawalCause.VOLUNTARY);
    }

    public static EnrollmentWithdrawal voluntaryBySystem() {
        return new EnrollmentWithdrawal(null, null, WithdrawalCause.VOLUNTARY);
    }

    public static EnrollmentWithdrawal forNonpayment() {
        return new EnrollmentWithdrawal(null, null, WithdrawalCause.NONPAYMENT);
    }

    public Optional<LocalDate> informedEffectiveDate() {
        return Optional.ofNullable(informedDate);
    }
}
