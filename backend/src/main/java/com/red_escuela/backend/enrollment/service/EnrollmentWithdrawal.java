package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.WithdrawalCause;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record EnrollmentWithdrawal(LocalDate informedDate, Long registeredByPersonId, WithdrawalCause cause, String reason) {

    public EnrollmentWithdrawal(LocalDate informedDate, Long registeredByPersonId, WithdrawalCause cause) {
        this(informedDate, registeredByPersonId, cause, null);
    }

    public EnrollmentWithdrawal {
        Objects.requireNonNull(cause, "cause");
    }

    public static EnrollmentWithdrawal voluntaryRegisteredBy(LocalDate informedDate, Long personId) {
        return new EnrollmentWithdrawal(informedDate, Objects.requireNonNull(personId, "personId"), WithdrawalCause.VOLUNTARY);
    }

    public static EnrollmentWithdrawal voluntaryRegisteredBy(LocalDate informedDate, Long personId, String reason) {
        return new EnrollmentWithdrawal(informedDate, Objects.requireNonNull(personId, "personId"), WithdrawalCause.VOLUNTARY,
                EnrollmentChange.normalizeRequired(reason));
    }

    public static EnrollmentWithdrawal forNonpayment() {
        return new EnrollmentWithdrawal(null, null, WithdrawalCause.NONPAYMENT);
    }

    public static EnrollmentWithdrawal forNonpaymentEffectiveOn(LocalDate effectiveDate) {
        return new EnrollmentWithdrawal(Objects.requireNonNull(effectiveDate, "effectiveDate"), null, WithdrawalCause.NONPAYMENT);
    }

    public Optional<LocalDate> informedEffectiveDate() {
        return Optional.ofNullable(informedDate);
    }
}
