package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InvoiceTestData {

    public static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST");
    public static final LocalDate ISSUE_DATE = LocalDate.of(2027, 3, 3);
    public static final LocalDate DUE_DATE = LocalDate.of(2027, 3, 13);
    public static final StudentSnapshot SNAPSHOT = new StudentSnapshot("Ana Perez", "30111222", "Calle 1");

    private InvoiceTestData() {
    }

    public static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    public static NonNegativeMoney usd(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.USD);
    }

    public static InvoiceDraftHeader header(InvoiceType type) {
        boolean debitNote = type == InvoiceType.LATE_FEE;
        return new InvoiceDraftHeader(1L, Currency.ARS, debitNote ? InvoiceDocumentType.DEBIT_NOTE : InvoiceDocumentType.INVOICE, type,
                InvoiceIssuingReason.MONTHLY_JOB, debitNote ? 99L : null);
    }

    public static FeeType feeTypeOf(InvoiceType type) {
        return type == InvoiceType.ANNUAL_UPFRONT ? FeeType.MONTHLY_INSTALLMENT : FeeType.valueOf(type.name());
    }

    public static InvoiceLineDraft line(FeeType feeType, String amount, String chargeKey) {
        return new InvoiceLineDraft(feeType, "Concepto " + chargeKey, YearMonth.of(2027, 3), 123L, null, null, null, 5L, chargeKey, ars(amount));
    }

    public static InvoiceLineDraft monthlyLine(String amount, String chargeKey) {
        return line(FeeType.MONTHLY_INSTALLMENT, amount, chargeKey);
    }

    public static InvoiceDiscountDraft discount(String base, String amount) {
        return new InvoiceDiscountDraft(DiscountType.SCHOLARSHIP, "Beca", null, ars(base), ars(amount), null, null);
    }

    public static Invoice monthlyDraft(String amount) {
        return InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT),
                monthlyLine(amount, "PE:123:MONTHLY_INSTALLMENT:2027-03"), Optional.empty(), ACTOR);
    }

    public static Invoice zeroTotalDraft() {
        return InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT),
                monthlyLine("80000.00", "PE:123:MONTHLY_INSTALLMENT:2027-03"), Optional.of(discount("80000.00", "80000.00")), ACTOR);
    }

    public static Invoice annualDraft(int lines) {
        List<InvoiceLineDraft> drafts = new java.util.ArrayList<>();
        for (int i = 1; i <= lines; i++) {
            drafts.add(monthlyLine("1000.00", "PE:123:MONTHLY_INSTALLMENT:2027-" + String.format("%02d", i)));
        }
        return InvoiceFactory.createAnnualUpfrontInvoiceDraft(header(InvoiceType.ANNUAL_UPFRONT), drafts, Map.of(), ACTOR);
    }

    public static void issue(Invoice invoice) {
        issue(invoice, ISSUE_DATE, DUE_DATE);
    }

    public static void issue(Invoice invoice, LocalDate issueDate, LocalDate dueDate) {
        invoice.issueInvoice(InvoiceNumber.formatInvoiceNumber(invoice.getDocumentType(), 1), issueDate, dueDate, SNAPSHOT, ACTOR);
    }

    public static Invoice issuedMonthly(String amount) {
        Invoice invoice = monthlyDraft(amount);
        issue(invoice);
        return invoice;
    }

    public static Invoice withId(Invoice invoice, long id) {
        ReflectionTestUtils.setField(invoice, "id", id);
        return invoice;
    }

    public static InvoiceCreditNoteApplication creditNote(CreditNoteApplicationKind kind, String offset, String free) {
        NonNegativeMoney offsetAmount = ars(offset);
        NonNegativeMoney freeAmount = ars(free);
        return new InvoiceCreditNoteApplication(kind, offsetAmount, freeAmount,
                List.of(new InvoiceLineCredit(1, offsetAmount.plus(freeAmount))));
    }
}
