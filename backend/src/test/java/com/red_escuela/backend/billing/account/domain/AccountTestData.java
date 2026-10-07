package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceNumber;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.DUE_DATE;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ISSUE_DATE;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.SNAPSHOT;

public final class AccountTestData {

    public static final long ACCOUNT_ID = 1L;
    public static final LocalDate DAY = LocalDate.of(2027, 3, 10);

    private AccountTestData() {
    }

    public static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    public static NonNegativeMoney usd(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.USD);
    }

    public static StudentCurrentAccount accountWithId(Currency currency, long id) {
        StudentCurrentAccount account = StudentCurrentAccount.openStudentCurrentAccount(10L, currency);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    public static StudentCurrentAccount arsAccount() {
        return accountWithId(Currency.ARS, ACCOUNT_ID);
    }

    public static Invoice issuedInvoice(long invoiceId, long accountId, Currency currency, String amount, String chargeKey) {
        InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, currency, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                InvoiceIssuingReason.AD_HOC, null);
        Invoice invoice = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line(FeeType.ADMINISTRATIVE_FEE, currency, amount, chargeKey), ACTOR);
        issue(invoice, invoiceId);
        return invoice;
    }

    public static Invoice issuedInvoice(long invoiceId, String amount) {
        return issuedInvoice(invoiceId, ACCOUNT_ID, Currency.ARS, amount, "PE:" + invoiceId + ":ADMINISTRATIVE_FEE");
    }

    public static Invoice issuedDebitNote(long invoiceId, String amount) {
        InvoiceDraftHeader header = new InvoiceDraftHeader(ACCOUNT_ID, Currency.ARS, InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE,
                InvoiceIssuingReason.MONTHLY_JOB, 99L);
        Invoice debitNote = InvoiceFactory.createLateFeeDebitNoteDraft(header,
                line(FeeType.LATE_FEE, Currency.ARS, amount, "LF:" + invoiceId), ACTOR);
        issue(debitNote, invoiceId);
        return debitNote;
    }

    public static Invoice issuedZeroTotalInvoice(long invoiceId) {
        Invoice invoice = com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.zeroTotalDraft();
        issue(invoice, invoiceId);
        return invoice;
    }

    public static Invoice draftInvoice(long invoiceId, String amount) {
        InvoiceDraftHeader header = new InvoiceDraftHeader(ACCOUNT_ID, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                InvoiceIssuingReason.AD_HOC, null);
        Invoice invoice = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header,
                line(FeeType.ADMINISTRATIVE_FEE, Currency.ARS, amount, "DR:" + invoiceId), ACTOR);
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        return invoice;
    }

    public static CurrentAccountEntry withEntryId(CurrentAccountEntry entry) {
        ReflectionTestUtils.setField(entry, "id", entry.getSequenceNumber() + 1000L);
        return entry;
    }

    private static InvoiceLineDraft line(FeeType feeType, Currency currency, String amount, String chargeKey) {
        return new InvoiceLineDraft(feeType, "Concepto " + chargeKey, YearMonth.of(2027, 3), null, null, null, null, null, chargeKey,
                NonNegativeMoney.of(new BigDecimal(amount), currency));
    }

    private static void issue(Invoice invoice, long invoiceId) {
        invoice.issueInvoice(InvoiceNumber.formatInvoiceNumber(invoice.getDocumentType(), invoiceId), ISSUE_DATE, DUE_DATE, SNAPSHOT, ACTOR);
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
    }
}
