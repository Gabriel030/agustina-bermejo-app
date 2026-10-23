package com.red_escuela.backend.billing.reporting.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CollectionsStockCalculator {

    private CollectionsStockCalculator() {
    }

    public static List<CollectionsStockRow> calculateStock(LocalDate businessDate, List<OverdueInvoiceFact> overdueInvoices) {
        List<CollectionsStockRow> rows = new ArrayList<>();
        for (Currency currency : Currency.values()) {
            Map<CollectionsStockScope, List<OverdueInvoiceFact>> invoicesByScope = new LinkedHashMap<>();
            invoicesByScope.put(CollectionsStockScope.TOTAL, new ArrayList<>());
            for (OverdueInvoiceFact invoice : overdueInvoices) {
                if (invoice.outstanding().currency() != currency || invoice.outstanding().isZero()) {
                    continue;
                }
                invoicesByScope.get(CollectionsStockScope.TOTAL).add(invoice);
                invoice.programIds().forEach(id -> invoicesByScope.computeIfAbsent(CollectionsStockScope.ofProgram(id), scope -> new ArrayList<>()).add(invoice));
                invoice.courseIds().forEach(id -> invoicesByScope.computeIfAbsent(CollectionsStockScope.ofCourse(id), scope -> new ArrayList<>()).add(invoice));
            }
            invoicesByScope.forEach((scope, invoices) -> rows.add(rowOf(businessDate, currency, scope, invoices)));
        }
        return List.copyOf(rows);
    }

    private static CollectionsStockRow rowOf(LocalDate businessDate, Currency currency, CollectionsStockScope scope, List<OverdueInvoiceFact> invoices) {
        Map<OverdueAgeBucket, Money> overdueByBucket = new EnumMap<>(OverdueAgeBucket.class);
        Set<Long> students = new HashSet<>();
        for (OverdueInvoiceFact invoice : invoices) {
            overdueByBucket.merge(invoice.bucketOn(businessDate), invoice.outstanding(), Money::plus);
            students.add(invoice.studentId());
        }
        return new CollectionsStockRow(businessDate, currency, scope, overdueByBucket, students.size());
    }
}
