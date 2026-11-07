package com.red_escuela.backend.billing.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulationInvariantsTest {

    private static final String LEDGER = "ENROLLMENT_FEE 2027-03 total=60000.00";

    @Test
    void invariantFailureMessageCarriesClockAndLedger() {
        SimulationInvariants invariants = new SimulationInvariants(() -> {
            throw new AssertionError("Billing invariants violated:\nI-02: account 7");
        }, List::of, () -> "2027-09-01T04:00-03:00", () -> LEDGER);

        assertThatThrownBy(() -> invariants.assertHoldAfter("MonthlyInvoiceIssuanceJob.run"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("[2027-09-01T04:00-03:00 after MonthlyInvoiceIssuanceJob.run]")
                .hasMessageContaining("I-02: account 7")
                .hasMessageContaining("Ledger:\n" + LEDGER);
    }

    @Test
    void passingInvariantsStayQuiet() {
        SimulationInvariants invariants = new SimulationInvariants(() -> { }, List::of, () -> "clock", () -> LEDGER);

        invariants.assertHoldAfter("step");
        invariants.assertNoPendingPublicationsAfter("step");

        assertThat(SimulationInvariants.failureMessage("c", "s", "v", "l")).isEqualTo("[c after s]\nv\nLedger:\nl");
    }
}
