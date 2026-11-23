package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.domain.PermissionDiscrepancyDirection;
import com.red_escuela.backend.identity.dto.PermissionDiscrepancyModuleSummary;
import com.red_escuela.backend.identity.dto.PermissionDiscrepancySummaryResponse;
import com.red_escuela.backend.identity.repository.PermissionDiscrepancyCounterRepository;
import com.red_escuela.backend.identity.repository.PermissionDiscrepancyDirectionTotalRow;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PermissionDiscrepancySummaryReader {

    private final PermissionDiscrepancyAccumulator accumulator;
    private final PermissionDiscrepancyCounterRepository counterRepository;

    public PermissionDiscrepancySummaryReader(PermissionDiscrepancyAccumulator accumulator,
                                              PermissionDiscrepancyCounterRepository counterRepository) {
        this.accumulator = accumulator;
        this.counterRepository = counterRepository;
    }

    @Transactional(readOnly = true)
    public PermissionDiscrepancySummaryResponse read() {
        accumulator.flush();
        Map<String, List<PermissionDiscrepancyDirectionTotalRow>> rowsByModule = new LinkedHashMap<>();
        for (PermissionDiscrepancyDirectionTotalRow row : counterRepository.sumByModuleAndDirection()) {
            rowsByModule.computeIfAbsent(row.getModule(), key -> new ArrayList<>()).add(row);
        }
        List<PermissionDiscrepancyModuleSummary> modules = new ArrayList<>();
        rowsByModule.forEach((module, rows) -> {
            long legacyAllowed = totalOf(rows, PermissionDiscrepancyDirection.LEGACY_ALLOWED_GRID_DENIED);
            long legacyDenied = totalOf(rows, PermissionDiscrepancyDirection.LEGACY_DENIED_GRID_ALLOWED);
            Instant lastSeenAt = rows.stream().map(PermissionDiscrepancyDirectionTotalRow::getLastSeenAt)
                    .max(Instant::compareTo).orElseThrow();
            modules.add(new PermissionDiscrepancyModuleSummary(module, legacyAllowed, legacyDenied,
                    legacyAllowed + legacyDenied, lastSeenAt));
        });
        return new PermissionDiscrepancySummaryResponse(List.copyOf(modules));
    }

    private static long totalOf(List<PermissionDiscrepancyDirectionTotalRow> rows, PermissionDiscrepancyDirection direction) {
        return rows.stream().filter(row -> direction.name().equals(row.getDirection()))
                .mapToLong(PermissionDiscrepancyDirectionTotalRow::getOccurrences).sum();
    }
}
