package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.domain.PermissionDiscrepancyDirection;
import com.red_escuela.backend.identity.dto.PermissionDiscrepancyResponse;
import com.red_escuela.backend.identity.exception.UnknownPermissionModuleException;
import com.red_escuela.backend.identity.repository.PermissionDiscrepancyCounterRepository;
import com.red_escuela.backend.identity.repository.PermissionRepository;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PermissionDiscrepancyReader {

    private final PermissionDiscrepancyAccumulator accumulator;
    private final PermissionDiscrepancyCounterRepository counterRepository;
    private final PermissionRepository permissionRepository;

    public PermissionDiscrepancyReader(PermissionDiscrepancyAccumulator accumulator,
                                       PermissionDiscrepancyCounterRepository counterRepository,
                                       PermissionRepository permissionRepository) {
        this.accumulator = accumulator;
        this.counterRepository = counterRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PermissionDiscrepancyResponse> read(String module, int page, int size) {
        if (module != null && !permissionRepository.findDistinctModules().contains(module)) {
            throw new UnknownPermissionModuleException(module);
        }
        accumulator.flush();
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        var result = counterRepository.findPageOrderedByLastSeen(module, PageRequest.of(boundedPage, boundedSize));
        return new PageResponse<>(result.getContent().stream()
                .map(row -> new PermissionDiscrepancyResponse(row.getRole(), row.getModule(), row.getPermissionCode(),
                        PermissionDiscrepancyDirection.valueOf(row.getDirection()), row.getOccurrences(), row.getLastSeenAt()))
                .toList(), boundedPage, boundedSize, result.getTotalElements());
    }
}
