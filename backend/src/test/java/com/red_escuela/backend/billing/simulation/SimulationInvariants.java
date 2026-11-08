package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.shared.time.MutableClock;
import org.awaitility.Awaitility;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

public final class SimulationInvariants {

    private final Runnable allInvariants;
    private final Supplier<List<String>> pendingPublications;
    private final Supplier<String> clockLabel;
    private final Supplier<String> ledgerTable;

    public SimulationInvariants(Runnable allInvariants, Supplier<List<String>> pendingPublications, Supplier<String> clockLabel, Supplier<String> ledgerTable) {
        this.allInvariants = allInvariants;
        this.pendingPublications = pendingPublications;
        this.clockLabel = clockLabel;
        this.ledgerTable = ledgerTable;
    }

    public static SimulationInvariants forDatabase(JdbcTemplate jdbc, MutableClock clock, Supplier<String> ledgerTable) {
        BillingInvariantChecker checker = BillingInvariantChecker.startingNow(jdbc);
        return new SimulationInvariants(checker::assertAllBillingInvariantsHold, checker::noEventPublicationIsPending,
                () -> OffsetDateTime.ofInstant(clock.instant(), clock.getZone()).toString(), ledgerTable);
    }

    public static String failureMessage(String clock, String step, String violations, String ledger) {
        return "[" + clock + " after " + step + "]\n" + violations + "\nLedger:\n" + ledger;
    }

    public void assertHoldAfter(String step) {
        try {
            allInvariants.run();
        } catch (AssertionError failure) {
            throw new AssertionError(failureMessage(clockLabel.get(), step, failure.getMessage(), ledgerTable.get()), failure);
        }
    }

    public void assertNoPendingPublicationsAfter(String step) {
        try {
            Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> pendingPublications.get().isEmpty());
        } catch (org.awaitility.core.ConditionTimeoutException timeout) {
            throw new AssertionError(failureMessage(clockLabel.get(), step, String.join("\n", pendingPublications.get()), ledgerTable.get()), timeout);
        }
    }
}
