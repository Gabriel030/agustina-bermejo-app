package com.red_escuela.backend.billing.run.domain;

import java.util.List;
import java.util.Set;

public record MonthlyInvoiceItemOutcome(MonthlyInvoiceRunItemResult result, String decisionCode, String errorCode, String message, List<Long> invoiceIds) {
    public static final String NO_CHARGES_PLANNED = "NO_CHARGES_PLANNED";
    private static final Set<String> INFORMATIVE_PLANNING_CODES = Set.of("ELIGIBLE", "CYCLE", "ISSUE_INSTALLMENT", "ISSUE_ENROLLMENT_FEE");
    private static final int MESSAGE_LIMIT = 500;

    public static MonthlyInvoiceItemOutcome issued(List<Long> invoiceIds) {
        return new MonthlyInvoiceItemOutcome(MonthlyInvoiceRunItemResult.ISSUED, null, null, null, List.copyOf(invoiceIds));
    }

    public static MonthlyInvoiceItemOutcome alreadyIssued(List<Long> invoiceIds) {
        return new MonthlyInvoiceItemOutcome(MonthlyInvoiceRunItemResult.ALREADY_ISSUED, null, null, null, List.copyOf(invoiceIds));
    }

    public static MonthlyInvoiceItemOutcome notApplicable(List<String> planningDecisionCodes) {
        String code = planningDecisionCodes.stream().filter(candidate -> !INFORMATIVE_PLANNING_CODES.contains(candidate)).findFirst().orElse(NO_CHARGES_PLANNED);
        return new MonthlyInvoiceItemOutcome(MonthlyInvoiceRunItemResult.NOT_APPLICABLE, code, null, null, List.of());
    }

    public static MonthlyInvoiceItemOutcome failed(String errorCode, String message) {
        String trimmed = message == null ? null : message.substring(0, Math.min(message.length(), MESSAGE_LIMIT));
        return new MonthlyInvoiceItemOutcome(MonthlyInvoiceRunItemResult.FAILED, null, errorCode, trimmed, List.of());
    }
}
