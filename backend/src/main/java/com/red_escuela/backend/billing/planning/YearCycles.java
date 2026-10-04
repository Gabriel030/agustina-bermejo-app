package com.red_escuela.backend.billing.planning;

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

    public Optional<YearCycle> covering(YearMonth month) {
        List<YearCycle> matches = cycles.stream().filter(c -> c.contains(month)).toList();
        return pick(matches);
    }

    public Optional<YearCycle> forCuota(YearMonth month) {
        List<YearCycle> matches = cycles.stream().filter(c -> c.termContains(month)).toList();
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
