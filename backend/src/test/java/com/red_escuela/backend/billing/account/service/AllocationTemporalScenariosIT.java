package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-094")
@Tag("D-144")
class AllocationTemporalScenariosIT extends AbstractCurrentAccountIT {

    private static final int GRACE_DAYS = 5;

    @Autowired
    private CurrentAccountAllocationService allocationService;

    static Stream<Arguments> monthBoundaries() {
        return Stream.of(
                Arguments.of(LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 5), "PENDING"),
                Arguments.of(LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 6), "OVERDUE"),
                Arguments.of(LocalDate.of(2027, 4, 30), LocalDate.of(2027, 5, 5), "PENDING"),
                Arguments.of(LocalDate.of(2027, 4, 30), LocalDate.of(2027, 5, 6), "OVERDUE"),
                Arguments.of(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 5), "PENDING"),
                Arguments.of(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 6), "OVERDUE"),
                Arguments.of(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 29), "PENDING"));
    }

    @ParameterizedTest(name = "due {0} reversed {1} is {2}")
    @MethodSource("monthBoundaries")
    void aReversalOnTheDueDatePlusGraceIsPendingAndTheNextDayIsOverdueAcrossMonthEndsAndLeapYears(LocalDate dueDate, LocalDate reversalDate, String expectedStatus) {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long invoiceId = issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", dueDate);
        LocalDate creditDate = dueDate.minusDays(3);
        inTransaction(() -> {
            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), nextExternalId(), ars("80000.00"), creditDate, "Pago");
            return allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(accountId), creditDate, ACTOR);
        });

        inTransaction(() -> allocationService.reverseNewestAllocationsUntilCreditCovers(service.lockStudentCurrentAccount(accountId), ars("80000.00"),
                reversalDate, GRACE_DAYS, "Contracargo", ACTOR));

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId)).isEqualTo(expectedStatus);
        invariants.assertAllBillingInvariantsHold();
    }
}
