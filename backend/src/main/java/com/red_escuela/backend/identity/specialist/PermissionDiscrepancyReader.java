package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.PermissionDiscrepancyResponse;
import com.red_escuela.backend.identity.repository.PermissionDiscrepancyCounterRepository;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PermissionDiscrepancyReader {

    private final PermissionDiscrepancyAccumulator accumulator;
    private final PermissionDiscrepancyCounterRepository counterRepository;

    public PermissionDiscrepancyReader(PermissionDiscrepancyAccumulator accumulator,
                                       PermissionDiscrepancyCounterRepository counterRepository) {
        this.accumulator = accumulator;
        this.counterRepository = counterRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PermissionDiscrepancyResponse> read(int page, int size) {
        accumulator.flush();
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        var result = counterRepository.findPageOrderedByLastSeen(PageRequest.of(boundedPage, boundedSize));
        return new PageResponse<>(result.getContent().stream()
                .map(row -> new PermissionDiscrepancyResponse(row.getRole(), row.getModule(), row.getPermissionCode(),
                        row.getOccurrences(), row.getLastSeenAt()))
                .toList(), boundedPage, boundedSize, result.getTotalElements());
    }
}
