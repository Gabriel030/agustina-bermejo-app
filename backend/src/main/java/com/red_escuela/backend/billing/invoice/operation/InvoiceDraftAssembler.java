package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;

final class InvoiceDraftAssembler {

    private static final int MAX_CONCEPT_LENGTH = 200;

    private InvoiceDraftAssembler() {
    }

    static Invoice assembleDraftInvoice(InvoiceIssuingContext context, Long studentCurrentAccountId) {
        InvoiceIssuingRequest request = context.request();
        InvoiceDraftHeader header = new InvoiceDraftHeader(studentCurrentAccountId, context.facts().offeringCurrency(), InvoiceDocumentType.INVOICE,
                request.invoiceType(), request.issuingReason(), null);
        InvoiceLineDraft line = new InvoiceLineDraft(request.feeType(), conceptOf(request), request.period(), request.programEnrollmentId(),
                request.courseEnrollmentId(), request.subjectId(), request.examBoardRegistrationId(), context.facts().priceRevisionId(),
                context.chargeKey().value(), context.facts().chargeAmount());
        Invoice draft = createDraftOfType(header, line, request);
        context.winningDiscount().ifPresent(winner -> draft.addDiscountToDraftLine(1, winner.toDiscountDraft()));
        return draft;
    }

    private static Invoice createDraftOfType(InvoiceDraftHeader header, InvoiceLineDraft line, InvoiceIssuingRequest request) {
        return switch (request.invoiceType()) {
            case MONTHLY_INSTALLMENT -> InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header, line, java.util.Optional.empty(), request.actor());
            case ENROLLMENT_FEE -> InvoiceFactory.createEnrollmentFeeInvoiceDraft(header, line, java.util.Optional.empty(), request.actor());
            case EXAM_FEE -> InvoiceFactory.createExamFeeInvoiceDraft(header, line, request.actor());
            case RETAKEN_SUBJECT_FEE -> InvoiceFactory.createRetakenSubjectFeeInvoiceDraft(header, line, request.actor());
            case DEGREE_ISSUANCE_FEE -> InvoiceFactory.createDegreeIssuanceFeeInvoiceDraft(header, line, request.actor());
            case ADMINISTRATIVE_FEE -> InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, request.actor());
            case ANNUAL_UPFRONT, LATE_FEE -> throw new IllegalStateException("Invoice type " + request.invoiceType() + " is not issued by the single charge operation");
        };
    }

    private static String conceptOf(InvoiceIssuingRequest request) {
        String concept = switch (request.feeType()) {
            case MONTHLY_INSTALLMENT -> "Cuota " + request.period();
            case ENROLLMENT_FEE -> "Matrícula " + request.period();
            case EXAM_FEE, RETAKEN_SUBJECT_FEE, DEGREE_ISSUANCE_FEE, ADMINISTRATIVE_FEE, LATE_FEE ->
                    request.description() == null || request.description().isBlank() ? request.feeType().name() : request.description().strip();
        };
        return concept.length() > MAX_CONCEPT_LENGTH ? concept.substring(0, MAX_CONCEPT_LENGTH) : concept;
    }
}
