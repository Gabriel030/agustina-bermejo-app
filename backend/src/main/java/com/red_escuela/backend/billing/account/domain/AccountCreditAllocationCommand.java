package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;
import java.util.Objects;

public record AccountCreditAllocationCommand(Long studentCurrentAccountId, LocalDate businessDate, StatusChangeActor actor) {

    public AccountCreditAllocationCommand {
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(businessDate, "businessDate");
        Objects.requireNonNull(actor, "actor");
    }
}
