package com.red_escuela.backend.notification.broadcast.audience.controller;

import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewResponse;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCatalogEntry;
import com.red_escuela.backend.notification.broadcast.audience.usecase.ListSegmentCatalogUseCase;
import com.red_escuela.backend.notification.broadcast.audience.usecase.PreviewBroadcastAudienceUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/broadcasts")
@PreAuthorize("hasAnyRole('DIRECTOR','ADMINISTRATIVO')")
public class BroadcastAudienceController {

    private final ListSegmentCatalogUseCase listSegmentCatalog;
    private final PreviewBroadcastAudienceUseCase previewBroadcastAudience;

    public BroadcastAudienceController(ListSegmentCatalogUseCase listSegmentCatalog,
                                       PreviewBroadcastAudienceUseCase previewBroadcastAudience) {
        this.listSegmentCatalog = listSegmentCatalog;
        this.previewBroadcastAudience = previewBroadcastAudience;
    }

    @GetMapping("/segment-catalog")
    public List<SegmentCatalogEntry> listSegmentCatalog() {
        return listSegmentCatalog.listSegmentCatalog();
    }

    @PostMapping("/audience-preview")
    public AudiencePreviewResponse previewBroadcastAudience(@Valid @RequestBody AudiencePreviewRequest request) {
        return previewBroadcastAudience.previewBroadcastAudience(request);
    }
}
