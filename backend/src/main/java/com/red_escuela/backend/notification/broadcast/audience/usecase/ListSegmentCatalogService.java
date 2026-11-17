package com.red_escuela.backend.notification.broadcast.audience.usecase;

import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCatalogEntry;
import com.red_escuela.backend.notification.broadcast.audience.specialist.SegmentResolver;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListSegmentCatalogService implements ListSegmentCatalogUseCase {

    private final SegmentResolver segmentResolver;

    public ListSegmentCatalogService(SegmentResolver segmentResolver) {
        this.segmentResolver = segmentResolver;
    }

    @Override
    public List<SegmentCatalogEntry> listSegmentCatalog() {
        return segmentResolver.availableSegments().stream()
                .map(descriptor -> new SegmentCatalogEntry(descriptor.key(), descriptor.parameters()))
                .toList();
    }
}
