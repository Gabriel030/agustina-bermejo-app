package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleSource;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

final class ReminderDigestScenarios {

    static final String ISSUE_DATE = "2027-03-01";

    private ReminderDigestScenarios() {
    }

    static ReminderInvoiceFact installment(long id, String dueDate) {
        return invoice(id, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING, dueDate,
                "80000.00", Currency.ARS, false, false);
    }

    static ReminderInvoiceFact installmentOf(long id, String dueDate, String amount, Currency currency) {
        return invoice(id, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING, dueDate, amount,
                currency, false, false);
    }

    static ReminderInvoiceFact enrollmentFee(long id, String dueDate) {
        return invoice(id, null, InvoiceDocumentType.INVOICE, InvoiceType.ENROLLMENT_FEE, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING, dueDate, "80000.00",
                Currency.ARS, false, false);
    }

    static ReminderInvoiceFact lateFeeNote(long id, long originId, String dueDate) {
        return invoice(id, originId, InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.OVERDUE, dueDate, "4000.00",
                Currency.ARS, false, false);
    }

    static ReminderInvoiceFact withReceiptUnderReview(ReminderInvoiceFact fact) {
        return invoice(fact.invoiceId(), fact.originInvoiceId(), fact.documentType(), fact.invoiceType(), fact.documentStatus(), fact.paymentStatus(),
                fact.dueDate().toString(), "80000.00", Currency.ARS, true, false);
    }

    static ReminderInvoiceFact withPaymentStatus(ReminderInvoiceFact fact, InvoicePaymentStatus paymentStatus) {
        return invoice(fact.invoiceId(), fact.originInvoiceId(), fact.documentType(), fact.invoiceType(), fact.documentStatus(), paymentStatus,
                fact.dueDate().toString(), "80000.00", Currency.ARS, false, false);
    }

    static ReminderInvoiceFact withOpenCancellation(ReminderInvoiceFact fact) {
        return invoice(fact.invoiceId(), fact.originInvoiceId(), fact.documentType(), fact.invoiceType(), fact.documentStatus(), fact.paymentStatus(),
                fact.dueDate().toString(), "80000.00", Currency.ARS, false, true);
    }

    static ReminderInvoiceFact invoice(long id, Long originId, InvoiceDocumentType documentType, InvoiceType invoiceType, InvoiceDocumentStatus documentStatus,
                                       InvoicePaymentStatus paymentStatus, String dueDate, String amount, Currency currency, boolean receiptUnderReview,
                                       boolean openCancellationRequest) {
        return new ReminderInvoiceFact(id, originId, documentType, invoiceType, documentStatus, paymentStatus, LocalDate.parse(ISSUE_DATE), LocalDate.parse(dueDate), null,
                NonNegativeMoney.of(new BigDecimal(amount), currency), 1L, null, receiptUnderReview, openCancellationRequest);
    }

    static ReminderNotice notice(String key, ReminderMessageType type, ReminderMilestone milestone, int offsetDays, ReminderChannel... channels) {
        return ReminderNotice.of(key, type, milestone, offsetDays, EnumSet.copyOf(List.of(channels)));
    }

    static ReminderNotice bothChannels(String key, ReminderMessageType type, ReminderMilestone milestone, int offsetDays) {
        return notice(key, type, milestone, offsetDays, ReminderChannel.IN_APP, ReminderChannel.EMAIL);
    }

    static ResolvedReminderSchedule schedule(ReminderNotice... notices) {
        return new ResolvedReminderSchedule(ReminderScheduleSource.TEMPLATE, 10L, 11L, 12L, 13L, List.of(notices), CollectionReminderSettings.initial());
    }
}
