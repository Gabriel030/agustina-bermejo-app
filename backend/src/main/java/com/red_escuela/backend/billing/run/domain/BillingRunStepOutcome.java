package com.red_escuela.backend.billing.run.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BillingRunStepOutcome(int candidates, int processed, int skipped, int failed, List<BillingRunItemRecord> items) {

    public BillingRunStepOutcome {
        items = List.copyOf(items);
    }

    public static BillingRunStepOutcome ofCandidates(int candidates, List<BillingRunItemRecord> reportedItems) {
        Map<String, BillingRunItemRecord> bySubject = new LinkedHashMap<>();
        reportedItems.forEach(item -> bySubject.putIfAbsent(item.subjectType() + ":" + item.subjectId(), item));
        Collection<BillingRunItemRecord> distinct = bySubject.values();
        int skipped = (int) distinct.stream().filter(item -> item.status() == BillingRunItemStatus.SKIPPED).count();
        int failed = distinct.size() - skipped;
        return new BillingRunStepOutcome(candidates, Math.max(0, candidates - skipped - failed), skipped, failed, List.copyOf(distinct));
    }

    public boolean hasErrors() {
        return failed > 0;
    }
}
