package com.red_escuela.backend.billing.invoice.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class OnlineOnlyInvoiceSelection {

    private OnlineOnlyInvoiceSelection() {
    }

    public static Set<Long> programEnrollmentIdsOf(Collection<Invoice> invoices) {
        Set<Long> ids = new LinkedHashSet<>();
        invoices.forEach(invoice -> invoice.getLines().stream().map(InvoiceLine::getProgramEnrollmentId).filter(id -> id != null).forEach(ids::add));
        return ids;
    }

    public static Set<Long> courseEnrollmentIdsOf(Collection<Invoice> invoices) {
        Set<Long> ids = new LinkedHashSet<>();
        invoices.forEach(invoice -> invoice.getLines().stream().map(InvoiceLine::getCourseEnrollmentId).filter(id -> id != null).forEach(ids::add));
        return ids;
    }

    public static Set<Long> invoiceIdsOfOnlineOnlyOfferings(Collection<Invoice> invoices, Set<Long> onlineOnlyProgramEnrollmentIds,
                                                            Set<Long> onlineOnlyCourseEnrollmentIds) {
        Set<Long> selected = new LinkedHashSet<>();
        for (Invoice invoice : invoices) {
            if (isEntirelyOnlineOnly(invoice, onlineOnlyProgramEnrollmentIds, onlineOnlyCourseEnrollmentIds)) {
                selected.add(invoice.getId());
            }
        }
        return selected;
    }

    private static boolean isEntirelyOnlineOnly(Invoice invoice, Set<Long> onlineOnlyProgramEnrollmentIds, Set<Long> onlineOnlyCourseEnrollmentIds) {
        boolean sawAnOffering = false;
        for (InvoiceLine line : invoice.getLines()) {
            Long programEnrollmentId = line.getProgramEnrollmentId();
            Long courseEnrollmentId = line.getCourseEnrollmentId();
            if (programEnrollmentId != null) {
                sawAnOffering = true;
                if (!onlineOnlyProgramEnrollmentIds.contains(programEnrollmentId)) {
                    return false;
                }
            }
            if (courseEnrollmentId != null) {
                sawAnOffering = true;
                if (!onlineOnlyCourseEnrollmentIds.contains(courseEnrollmentId)) {
                    return false;
                }
            }
        }
        return sawAnOffering;
    }
}
