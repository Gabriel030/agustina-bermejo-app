package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.InvoiceLineNotAllowedForInvoiceTypeException;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InvoiceFactory {

    private InvoiceFactory() {
    }

    public static Invoice createMonthlyInstallmentInvoiceDraft(InvoiceDraftHeader header, InvoiceLineDraft line,
                                                               Optional<InvoiceDiscountDraft> discount, StatusChangeActor actor) {
        return createSingleLineDraft(InvoiceType.MONTHLY_INSTALLMENT, header, line, discount, actor);
    }

    public static Invoice createAnnualUpfrontInvoiceDraft(InvoiceDraftHeader header, List<InvoiceLineDraft> monthlyLines,
                                                          Map<Integer, InvoiceDiscountDraft> discountsByLineNumber, StatusChangeActor actor) {
        requireInvoiceType(header, InvoiceType.ANNUAL_UPFRONT);
        Invoice invoice = Invoice.createDraftInvoice(header, actor);
        monthlyLines.forEach(invoice::addChargeLineToDraft);
        discountsByLineNumber.forEach(invoice::addDiscountToDraftLine);
        return invoice;
    }

    public static Invoice createEnrollmentFeeInvoiceDraft(InvoiceDraftHeader header, InvoiceLineDraft line,
                                                          Optional<InvoiceDiscountDraft> discount, StatusChangeActor actor) {
        return createSingleLineDraft(InvoiceType.ENROLLMENT_FEE, header, line, discount, actor);
    }

    public static Invoice createExamFeeInvoiceDraft(InvoiceDraftHeader header, InvoiceLineDraft line, StatusChangeActor actor) {
        return createSingleLineDraft(InvoiceType.EXAM_FEE, header, line, Optional.empty(), actor);
    }

    public static Invoice createRetakenSubjectFeeInvoiceDraft(InvoiceDraftHeader header, InvoiceLineDraft line, StatusChangeActor actor) {
        return createSingleLineDraft(InvoiceType.RETAKEN_SUBJECT_FEE, header, line, Optional.empty(), actor);
    }

    public static Invoice createDegreeIssuanceFeeInvoiceDraft(InvoiceDraftHeader header, InvoiceLineDraft line, StatusChangeActor actor) {
        return createSingleLineDraft(InvoiceType.DEGREE_ISSUANCE_FEE, header, line, Optional.empty(), actor);
    }

    public static Invoice createAdministrativeFeeInvoiceDraft(InvoiceDraftHeader header, InvoiceLineDraft line, StatusChangeActor actor) {
        return createSingleLineDraft(InvoiceType.ADMINISTRATIVE_FEE, header, line, Optional.empty(), actor);
    }

    public static Invoice createLateFeeDebitNoteDraft(InvoiceDraftHeader header, InvoiceLineDraft line, StatusChangeActor actor) {
        return createSingleLineDraft(InvoiceType.LATE_FEE, header, line, Optional.empty(), actor);
    }

    private static Invoice createSingleLineDraft(InvoiceType expectedType, InvoiceDraftHeader header, InvoiceLineDraft line,
                                                 Optional<InvoiceDiscountDraft> discount, StatusChangeActor actor) {
        requireInvoiceType(header, expectedType);
        Invoice invoice = Invoice.createDraftInvoice(header, actor);
        invoice.addChargeLineToDraft(line);
        discount.ifPresent(discountDraft -> invoice.addDiscountToDraftLine(1, discountDraft));
        return invoice;
    }

    private static void requireInvoiceType(InvoiceDraftHeader header, InvoiceType expectedType) {
        if (header.invoiceType() != expectedType) {
            throw new InvoiceLineNotAllowedForInvoiceTypeException();
        }
    }
}
