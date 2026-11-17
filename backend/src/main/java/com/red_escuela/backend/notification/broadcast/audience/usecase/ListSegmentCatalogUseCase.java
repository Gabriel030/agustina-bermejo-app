package com.red_escuela.backend.notification.broadcast.audience.usecase;

import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCatalogEntry;

import java.util.List;

public interface ListSegmentCatalogUseCase {
    List<SegmentCatalogEntry> listSegmentCatalog();
}
