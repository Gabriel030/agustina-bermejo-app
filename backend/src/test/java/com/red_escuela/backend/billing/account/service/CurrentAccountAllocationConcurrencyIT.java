package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.SNAPSHOT;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-136")
@Tag("D-144")
class CurrentAccountAllocationConcurrencyIT extends AbstractCurrentAccountIT {

    @Autowired
    private CurrentAccountAllocationService allocationService;

    private void issueAdministrativeFee(Long accountId, String amount, LocalDate dueDate) {
        inTransaction(() -> {
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                    InvoiceIssuingReason.AD_HOC_CHARGE, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Concepto", YearMonth.of(2027, 3), null, null, null, null, null,
                    "CC:" + System.nanoTime(), ars(amount));
            Invoice invoice = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
            invoice.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), dueDate.minusDays(9), dueDate, SNAPSHOT, ACTOR);
            Invoice saved = invoiceRepository.save(invoice);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, dueDate.minusDays(9));
            return saved.getId();
        });
    }

    @Test
    void twoCreditsAtTheSameTimeAllocateEachInvoiceExactlyOnce() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        issueAdministrativeFee(accountId, "80000.00", LocalDate.of(2027, 3, 10));
        issueAdministrativeFee(accountId, "80000.00", LocalDate.of(2027, 4, 10));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return inTransaction(() -> {
                        var account = service.lockStudentCurrentAccount(accountId);
                        service.recordPaymentReceivedCredit(account, nextExternalId(), ars("80000.00"), LocalDate.of(2027, 4, 1), "Pago");
                        return allocationService.allocateAccountCreditToOldestInvoices(account, LocalDate.of(2027, 4, 1), ACTOR);
                    });
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from payment_allocation where student_current_account_id = ?", Long.class, accountId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("select count(distinct invoice_id) from payment_allocation where student_current_account_id = ?", Long.class, accountId)).isEqualTo(2L);
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForList("select payment_status from invoice where student_current_account_id = ?", String.class, accountId)).containsOnly("PAID");
        invariants.assertAllBillingInvariantsHold();
    }
}
