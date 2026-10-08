package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeObligationStatus;
import com.red_escuela.backend.billing.planning.domain.ObligationInvoiceFacts;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class EnrollmentFeeObligationStatusClassifier {

    public record Evidence(boolean exempted, Optional<ObligationInvoiceFacts> invoice) {
        boolean invoiceHas(Predicate<ObligationInvoiceFacts> condition) {
            return invoice.filter(condition).isPresent();
        }
    }

    private record Rule(Predicate<Evidence> condition, EnrollmentFeeObligationStatus status) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(evidence -> evidence.invoice().isEmpty() && evidence.exempted(), EnrollmentFeeObligationStatus.EXEMPTED),
            new Rule(evidence -> evidence.invoice().isEmpty(), EnrollmentFeeObligationStatus.NOT_ISSUED),
            new Rule(evidence -> evidence.invoiceHas(invoice -> invoice.status() == InvoiceStatus.PAID), EnrollmentFeeObligationStatus.PAID),
            new Rule(evidence -> evidence.invoiceHas(invoice -> invoice.status() == InvoiceStatus.WAIVED), EnrollmentFeeObligationStatus.WAIVED),
            new Rule(Evidence::exempted, EnrollmentFeeObligationStatus.EXEMPTED),
            new Rule(evidence -> evidence.invoiceHas(invoice -> invoice.status() == InvoiceStatus.CANCELLED), EnrollmentFeeObligationStatus.CANCELLED),
            new Rule(evidence -> evidence.invoiceHas(ObligationInvoiceFacts::receiptUnderReview), EnrollmentFeeObligationStatus.IN_REVIEW),
            new Rule(evidence -> evidence.invoiceHas(ObligationInvoiceFacts::untouched), EnrollmentFeeObligationStatus.PENDING),
            new Rule(evidence -> true, EnrollmentFeeObligationStatus.PARTIAL));

    public EnrollmentFeeObligationStatus classifyEnrollmentFeeObligation(Evidence evidence) {
        return RULES.stream().filter(rule -> rule.condition().test(evidence)).findFirst().orElseThrow().status();
    }
}
