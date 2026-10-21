package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository;
import com.red_escuela.backend.billing.dto.AdminInvoiceResponse;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.dto.MeInvoiceResponse;
import com.red_escuela.backend.billing.invoice.dto.PriceBreakdownCreditNoteResponse;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceViewLoader;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-376")
@Tag("D-139")
class AllocationSourcesIT extends AbstractCurrentAccountIT {

    private static final LocalDate TODAY = LocalDate.of(2027, 4, 20);
    private static final int GRACE_DAYS = 5;

    @Autowired
    private CurrentAccountAllocationService allocationService;
    @Autowired
    private PaymentAllocationRepository allocationRepository;
    @Autowired
    private InvoiceQueryService invoiceQueryService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceViewLoader invoiceViewLoader;

    private BillingInvariantChecker invariants;
    private Long accountId;

    @BeforeEach
    void openAccountAndStartInvariants() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
        accountId = newAccountId(newStudentId(), Currency.ARS);
    }

    private Long issue(String amount) {
        return issueAdministrativeFeeWithLedgerDebit(accountId, amount, LocalDate.of(2027, 3, 10));
    }

    private long receivePayment(String amount, LocalDate paidOn) {
        long paymentId = BillingTestFixtures.seedApprovedPaymentRow(accountId, ars(amount));
        inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), paymentId, ars(amount), paidOn, "Pago"));
        return paymentId;
    }

    private long receiveCreditNote(String freeCredit, LocalDate issuedOn) {
        Long otherInvoice = issueAdministrativeFeeWithLedgerDebit(accountId, "1000.00", LocalDate.of(2027, 6, 10));
        long creditNoteId = newCreditNoteId(accountId, otherInvoice, "0.00", freeCredit);
        inTransaction(() -> service.recordCreditNoteIssuedCredit(service.lockStudentCurrentAccount(accountId), creditNoteId, ars("0.00"), ars(freeCredit),
                issuedOn, "Nota de crédito"));
        return creditNoteId;
    }

    private void allocate() {
        inTransaction(() -> allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(accountId), TODAY, ACTOR));
    }

    private List<Map<String, Object>> sourcesOf(Long invoiceId) {
        return jdbc.queryForList("select s.payment_id, s.credit_note_id, s.amount, s.credit_entry_id, s.reverses_source_id, a.reverses_allocation_id "
                + "from payment_allocation_source s join payment_allocation a on a.id = s.payment_allocation_id where a.invoice_id = ? order by s.id", invoiceId);
    }

    private Long currentAllocationOf(Long invoiceId) {
        return jdbc.queryForObject("select id from payment_allocation where invoice_id = ? and reverses_allocation_id is null", Long.class, invoiceId);
    }

    @Test
    void twoPaymentsThatFinanceOneInvoiceLeaveTwoSourcesWithTheAmountTakenFromEach() {
        Long invoice = issue("8000.00");
        long first = receivePayment("3000.00", LocalDate.of(2027, 3, 5));
        long second = receivePayment("5000.00", LocalDate.of(2027, 3, 12));

        allocate();

        List<Map<String, Object>> sources = sourcesOf(invoice);
        assertThat(sources).extracting(row -> row.get("payment_id")).containsExactly(first, second);
        assertThat(sources).extracting(row -> (BigDecimal) row.get("amount")).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3000.00"), new BigDecimal("5000.00"));
        assertThat(sources).extracting(row -> row.get("credit_note_id")).containsOnlyNulls();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void onePaymentThatFinancesTwoInvoicesIsASourceOfBothWithoutExceedingItself() {
        Long first = issue("6000.00");
        Long second = issue("4000.00");
        long payment = receivePayment("10000.00", LocalDate.of(2027, 3, 5));

        allocate();

        assertThat(sourcesOf(first)).singleElement().satisfies(row -> {
            assertThat(row.get("payment_id")).isEqualTo(payment);
            assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("6000.00");
        });
        assertThat(sourcesOf(second)).singleElement().satisfies(row -> {
            assertThat(row.get("payment_id")).isEqualTo(payment);
            assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("4000.00");
        });
        assertThat(amount("select sum(s.amount) from payment_allocation_source s where s.payment_id = ?", payment)).isEqualByComparingTo("10000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aCreditNoteAndAPaymentThatFinanceOneInvoiceAreBothSources() {
        long creditNote = receiveCreditNote("2000.00", LocalDate.of(2027, 3, 5));
        Long invoice = issue("5000.00");
        long payment = receivePayment("3000.00", LocalDate.of(2027, 3, 12));

        allocate();

        List<Map<String, Object>> sources = sourcesOf(invoice);
        assertThat(sources).extracting(row -> row.get("credit_note_id")).containsExactly(creditNote, null);
        assertThat(sources).extracting(row -> row.get("payment_id")).containsExactly(null, payment);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theBreakdownShowsACreditNoteSourceAsSuchWithoutAPaymentId() {
        long creditNote = receiveCreditNote("5000.00", LocalDate.of(2027, 3, 5));
        Long invoice = issue("5000.00");

        allocate();

        String number = jdbc.queryForObject("select credit_note_number from credit_note where id = ?", String.class, creditNote);
        List<PriceBreakdownCreditNoteResponse> creditNotes = inTransaction(() -> invoiceQueryService.priceBreakdown(invoice).creditNotesUsedAsFreeCredit());
        assertThat(creditNotes).singleElement().satisfies(note -> {
            assertThat(note.creditNoteId()).isEqualTo(creditNote);
            assertThat(note.description()).isEqualTo("Nota de crédito " + number);
            assertThat(note.amount().amount()).isEqualByComparingTo("5000.00");
        });
        assertThat(inTransaction(() -> invoiceQueryService.priceBreakdown(invoice).paymentsApplied())).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theInvoiceViewListsTheCreditNoteAndThePaymentThatFinancedItInEveryResponse() {
        long creditNote = receiveCreditNote("2000.00", LocalDate.of(2027, 3, 5));
        Long invoice = issue("5000.00");
        long payment = receivePayment("3000.00", LocalDate.of(2027, 3, 12));
        allocate();
        String number = jdbc.queryForObject("select credit_note_number from credit_note where id = ?", String.class, creditNote);

        List<Object> responses = inTransaction(() -> {
            var view = invoiceViewLoader.loadInvoiceView(invoiceRepository.findInvoiceWithLinesById(invoice).orElseThrow());
            return List.<Object>of(InvoiceResponse.from(view), MeInvoiceResponse.of(view, List.of()), AdminInvoiceResponse.of(view, List.of()));
        });

        InvoiceResponse base = (InvoiceResponse) responses.get(0);
        List<PriceBreakdownCreditNoteResponse> meNotes = ((MeInvoiceResponse) responses.get(1)).creditNotes();
        List<PriceBreakdownCreditNoteResponse> adminNotes = ((AdminInvoiceResponse) responses.get(2)).creditNotes();
        for (List<PriceBreakdownCreditNoteResponse> notes : List.of(base.creditNotes(), meNotes, adminNotes)) {
            assertThat(notes).singleElement().satisfies(note -> {
                assertThat(note.creditNoteId()).isEqualTo(creditNote);
                assertThat(note.creditNoteNumber()).isEqualTo(number);
                assertThat(note.description()).isEqualTo("Nota de crédito " + number);
                assertThat(note.amount().amount()).isEqualByComparingTo("2000.00");
            });
        }
        assertThat(((MeInvoiceResponse) responses.get(1)).payments()).singleElement().satisfies(share -> {
            assertThat(share.paymentId()).isEqualTo(payment);
            assertThat(share.amountApplied().amount()).isEqualByComparingTo("3000.00");
        });
        assertThat(((AdminInvoiceResponse) responses.get(2)).payments()).extracting(share -> share.paymentId()).containsExactly(payment);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void anInvoiceFinancedOnlyByPaymentsHasNoCreditNotesInItsView() {
        Long invoice = issue("3000.00");
        receivePayment("3000.00", LocalDate.of(2027, 3, 5));
        allocate();

        InvoiceResponse response = inTransaction(() -> InvoiceResponse.from(invoiceViewLoader.loadInvoiceView(invoiceRepository.findInvoiceWithLinesById(invoice).orElseThrow())));

        assertThat(response.creditNotes()).isEmpty();
    }

    @Test
    void reversingTheAllocationAlsoReversesItsSourcesAndTheBreakdownNoLongerListsThem() {
        Long invoice = issue("8000.00");
        receivePayment("3000.00", LocalDate.of(2027, 3, 5));
        receivePayment("5000.00", LocalDate.of(2027, 3, 12));
        allocate();
        PaymentAllocation allocation = allocationRepository.findById(currentAllocationOf(invoice)).orElseThrow();

        inTransaction(() -> allocationService.reverseAllocation(service.lockStudentCurrentAccount(accountId), allocation, TODAY, GRACE_DAYS, "Contracargo", ACTOR));

        List<Map<String, Object>> sources = sourcesOf(invoice);
        assertThat(sources).hasSize(4);
        assertThat(sources.subList(2, 4)).extracting(row -> row.get("reverses_source_id")).doesNotContainNull();
        assertThat(amount("select sum(case when a.reverses_allocation_id is null then s.amount else -s.amount end) from payment_allocation_source s "
                + "join payment_allocation a on a.id = s.payment_allocation_id where a.invoice_id = ?", invoice)).isEqualByComparingTo("0.00");
        assertThat(inTransaction(() -> invoiceQueryService.priceBreakdown(invoice).paymentsApplied())).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theDatabaseRejectsAnExtraSourceThatBreaksTheSumOfTheAllocation() {
        Long invoice = issue("8000.00");
        receivePayment("3000.00", LocalDate.of(2027, 3, 5));
        receivePayment("5000.00", LocalDate.of(2027, 3, 12));
        receivePayment("1.00", LocalDate.of(2027, 3, 13));
        allocate();
        Long allocationId = currentAllocationOf(invoice);
        Map<String, Object> spare = jdbc.queryForMap("select id, payment_id from current_account_entry where student_current_account_id = ? "
                + "and entry_type = 'PAYMENT_RECEIVED' order by sequence_number desc limit 1", accountId);

        assertThatThrownBy(() -> inTransaction(() -> jdbc.update("insert into payment_allocation_source (payment_allocation_id, student_current_account_id, "
                        + "credit_entry_id, payment_id, amount, amount_currency, recorded_at) values (?, ?, ?, ?, 1.00, 'ARS', ?)",
                allocationId, accountId, spare.get("id"), spare.get("payment_id"), LocalDateTime.now())))
                .hasMessageContaining("must add up to its amount");
    }

    @Test
    void theDatabaseRejectsASourceWhosePaymentIsNotTheOneOfItsCreditEntry() {
        Long invoice = issue("8000.00");
        receivePayment("8000.00", LocalDate.of(2027, 3, 5));
        allocate();
        Long allocationId = currentAllocationOf(invoice);
        Long entryId = jdbc.queryForObject("select credit_entry_id from payment_allocation_source where payment_allocation_id = ?", Long.class, allocationId);

        assertThatThrownBy(() -> inTransaction(() -> jdbc.update("insert into payment_allocation_source (payment_allocation_id, student_current_account_id, "
                        + "credit_entry_id, payment_id, amount, amount_currency, recorded_at) values (?, ?, ?, ?, 1.00, 'ARS', ?)",
                allocationId, accountId, entryId, nextExternalId(), LocalDateTime.now())))
                .hasMessageContaining("payment or credit note credit entry");
    }

    private void withTriggerDisabled(String table, String trigger, Runnable work) {
        jdbc.execute("alter table " + table + " disable trigger " + trigger);
        try {
            work.run();
        } finally {
            jdbc.execute("alter table " + table + " enable trigger " + trigger);
        }
    }

    private CurrentAccountEntry entryOfPayment(long paymentId) {
        return entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId).stream()
                .filter(entry -> Long.valueOf(paymentId).equals(entry.getPaymentId())).findFirst().orElseThrow();
    }

    private List<Object> currentPaymentsOf(Long invoiceId) {
        return jdbc.queryForList("select s.payment_id from payment_allocation_source s join payment_allocation a on a.id = s.payment_allocation_id "
                + "where a.invoice_id = ? and a.reverses_allocation_id is null and not exists (select 1 from payment_allocation r where r.reverses_allocation_id = a.id) "
                + "order by s.id", Object.class, invoiceId);
    }

    @Test
    void anOffsetOnlyCreditNoteNeverBecomesTheSourceOfAnAllocationFundedByACashPayment() {
        issueAdministrativeFeeWithLedgerDebit(accountId, "150.00", LocalDate.of(2027, 6, 10));
        long payment = receivePayment("100.00", LocalDate.of(2027, 3, 5));
        Long cancelled = issueAdministrativeFeeWithLedgerDebit(accountId, "150.00", LocalDate.of(2027, 7, 10));
        long creditNote = newCreditNoteId(accountId, cancelled, "150.00", "0.00");
        inTransaction(() -> service.recordCreditNoteIssuedCredit(service.lockStudentCurrentAccount(accountId), creditNote, ars("150.00"), ars("0.00"),
                LocalDate.of(2027, 3, 6), "Anulación"));
        Long invoice = issue("100.00");

        allocate();

        assertThat(sourcesOf(invoice)).singleElement().satisfies(row -> {
            assertThat(row.get("payment_id")).isEqualTo(payment);
            assertThat(row.get("credit_note_id")).isNull();
        });
        assertThat(jdbc.queryForObject("select effective_date from payment_allocation where invoice_id = ?", LocalDate.class, invoice)).isEqualTo(LocalDate.of(2027, 3, 5));
        assertThat(invariants.everyAllocationIsFundedByItsSources()).isEmpty();
    }

    @Test
    void reversingAnAllocationAndAllocatingAgainFundsItWithTheCreditItReleasedAndNotWithTheOneOfAnotherInvoice() {
        Long first = issue("100.00");
        Long second = issueAdministrativeFeeWithLedgerDebit(accountId, "100.00", LocalDate.of(2027, 3, 11));
        long firstPayment = receivePayment("100.00", LocalDate.of(2027, 3, 5));
        long secondPayment = receivePayment("100.00", LocalDate.of(2027, 3, 6));
        allocate();
        assertThat(currentPaymentsOf(first)).containsExactly(firstPayment);
        assertThat(currentPaymentsOf(second)).containsExactly(secondPayment);
        PaymentAllocation allocation = allocationRepository.findById(currentAllocationOf(first)).orElseThrow();
        inTransaction(() -> allocationService.reverseAllocation(service.lockStudentCurrentAccount(accountId), allocation, TODAY, GRACE_DAYS, "Contracargo", ACTOR));

        allocate();

        assertThat(currentPaymentsOf(first)).containsExactly(firstPayment);
        assertThat(currentPaymentsOf(second)).containsExactly(secondPayment);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aReversedPaymentIsNotUsedAsASourceAndTheDatabaseRejectsItAsOne() {
        long bounced = receivePayment("100.00", LocalDate.of(2027, 3, 5));
        long good = receivePayment("60.00", LocalDate.of(2027, 3, 6));
        CurrentAccountEntry bouncedEntry = entryOfPayment(bounced);
        inTransaction(() -> service.recordPaymentReversalDebit(service.lockStudentCurrentAccount(accountId), bouncedEntry, TODAY, "Rebote"));
        Long invoice = issue("60.00");

        allocate();

        assertThat(currentPaymentsOf(invoice)).containsExactly(good);
        Long allocationId = currentAllocationOf(invoice);
        assertThatThrownBy(() -> inTransaction(() -> jdbc.update("insert into payment_allocation_source (payment_allocation_id, student_current_account_id, "
                        + "credit_entry_id, payment_id, amount, amount_currency, recorded_at) values (?, ?, ?, ?, 1.00, 'ARS', ?)",
                allocationId, accountId, bouncedEntry.getId(), bounced, LocalDateTime.now()))).hasMessageContaining("reversed payment cannot be the source");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void bouncingAPaymentThatFundsAnAllocationReversesThatAllocationAndTheDatabaseBlocksBouncingItBefore() {
        Long first = issue("100.00");
        Long second = issueAdministrativeFeeWithLedgerDebit(accountId, "100.00", LocalDate.of(2027, 3, 11));
        long bounced = receivePayment("100.00", LocalDate.of(2027, 3, 5));
        long kept = receivePayment("100.00", LocalDate.of(2027, 3, 6));
        allocate();
        receivePayment("100.00", LocalDate.of(2027, 3, 7));
        CurrentAccountEntry bouncedEntry = entryOfPayment(bounced);
        assertThatThrownBy(() -> inTransaction(() -> service.recordPaymentReversalDebit(service.lockStudentCurrentAccount(accountId), bouncedEntry, TODAY, "Rebote")))
                .hasMessageContaining("still funds an allocation");

        inTransaction(() -> {
            var account = service.lockStudentCurrentAccount(accountId);
            allocationService.reverseAllocationsFundedByPayment(account, bounced, TODAY, GRACE_DAYS, "Rebote", ACTOR);
            service.recordPaymentReversalDebit(account, bouncedEntry, TODAY, "Rebote");
            return null;
        });

        assertThat(currentPaymentsOf(first)).isEmpty();
        assertThat(currentPaymentsOf(second)).containsExactly(kept);
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, first)).isNotEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theDatabaseRejectsTakingAnEntryBeyondItsAllocatableAmount() {
        issue("100.00");
        receivePayment("100.00", LocalDate.of(2027, 3, 5));
        allocate();
        Long other = issueAdministrativeFeeWithLedgerDebit(accountId, "50.00", LocalDate.of(2027, 6, 10));
        Map<String, Object> entry = jdbc.queryForMap("select id, payment_id from current_account_entry where student_current_account_id = ? "
                + "and entry_type = 'PAYMENT_RECEIVED'", accountId);

        assertThatThrownBy(() -> inTransaction(() -> {
            Long extra = jdbc.queryForObject("insert into payment_allocation (student_current_account_id, invoice_id, amount, amount_currency, "
                    + "effective_date, allocated_on, actor_process, recorded_at) values (?, ?, 50.00, 'ARS', ?, ?, 'TEST', ?) returning id",
                    Long.class, accountId, other, TODAY, TODAY, LocalDateTime.now());
            jdbc.update("insert into payment_allocation_source (payment_allocation_id, student_current_account_id, credit_entry_id, payment_id, amount, "
                    + "amount_currency, recorded_at) values (?, ?, ?, ?, 50.00, 'ARS', ?)", extra, accountId, entry.get("id"), entry.get("payment_id"), LocalDateTime.now());
            return null;
        })).hasMessageContaining("taken beyond its allocatable amount");
    }

    @Test
    void theDatabaseRejectsAnAllocationWithoutSources() {
        Long invoice = issue("8000.00");

        assertThatThrownBy(() -> jdbc.update("insert into payment_allocation (student_current_account_id, invoice_id, amount, amount_currency, effective_date, "
                + "allocated_on, actor_process, recorded_at) values (?, ?, 8000.00, 'ARS', ?, ?, 'TEST', ?)", accountId, invoice, TODAY, TODAY, LocalDateTime.now()))
                .hasMessageContaining("must add up to its amount");
    }

    @Test
    void theInvariantIsRedWhenAnAllocationHasNoSources() {
        Long invoice = issue("8000.00");
        withTriggerDisabled("payment_allocation", "trg_payment_allocation_has_sources", () ->
                jdbc.update("insert into payment_allocation (student_current_account_id, invoice_id, amount, amount_currency, effective_date, allocated_on, "
                        + "actor_process, recorded_at) values (?, ?, 8000.00, 'ARS', ?, ?, 'TEST', ?)", accountId, invoice, TODAY, TODAY, LocalDateTime.now()));

        assertThat(invariants.everyAllocationIsFundedByItsSources()).singleElement().asString().contains("I-20 allocation whose sources do not add up");
    }
}
