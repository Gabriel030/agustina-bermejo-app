package com.red_escuela.backend.architecture.fixture.billing.ledger;

import org.springframework.data.jpa.repository.Modifying;

public interface ModifyingAccountRepositoryFixture {

    @Modifying
    int insertStudentCurrentAccountIfAbsent();

    @Modifying
    int resetBalance();
}
