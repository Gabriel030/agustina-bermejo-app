package com.red_escuela.backend.billing.planning.domain;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class YearCycles {
    private final List<YearCycle> cycles;
    private final Long preferredTemplateId;

    public YearCycles(List<YearCycle> cycles, Long preferredTemplateId) {
        this.cycles = cycles.stream().sorted(Comparator.comparing(YearCycle::start)).map(c -> withLowerBound(c, cycles)).toList();
        this.preferredTemplateId = preferredTemplateId;
    }

    private static YearCycle withLowerBound(YearCycle cycle, List<YearCycle> all) {
        return all.stream().filter(o -> o != cycle && o.end().isBefore(cycle.start())).map(YearCycle::end).max(Comparator.naturalOrder())
                .map(previousEnd -> cycle.withCoverageStart(previousEnd.plusMonths(1))).orElseGet(() -> cycle.withCoverageStart(YearMonth.of(cycle.start().getYear(), 1)));
    }

    public List<YearCycle> all() {
        return cycles;
    }

    public boolean isConfiguredFor(YearMonth month) {
        return cycles.stream().anyMatch(c -> !c.end().isBefore(month));
    }

    public boolean hasGapAt(YearMonth month) {
        boolean touched = cycles.stream().anyMatch(c -> c.start().getYear() <= month.getYear() && c.end().getYear() >= month.getYear());
        boolean before = cycles.stream().anyMatch(c -> c.end().getYear() < month.getYear());
        boolean after = cycles.stream().anyMatch(c -> c.start().getYear() > month.getYear());
        return !touched && before && after;
    }

    public Optional<YearCycle> covering(YearMonth month) {
        List<YearCycle> matches = cycles.stream().filter(c -> c.contains(month)).toList();
        return pick(matches);
    }

    public Optional<YearCycle> forInstallment(YearMonth month) {
        List<YearCycle> matches = cycles.stream().filter(c -> c.installmentRangeContains(month)).toList();
        return pick(matches);
    }

    public Optional<YearCycle> latestStartedOn(YearMonth month) {
        return cycles.stream().filter(c -> !c.start().isAfter(month)).max(Comparator.comparing(YearCycle::start));
    }

    private Optional<YearCycle> pick(List<YearCycle> matches) {
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (preferredTemplateId != null) {
            Optional<YearCycle> preferred = matches.stream().filter(c -> preferredTemplateId.equals(c.templateId())).findFirst();
            if (preferred.isPresent()) {
                return preferred;
            }
        }
        return matches.stream().max(Comparator.comparing(YearCycle::termStart));
    }
}
