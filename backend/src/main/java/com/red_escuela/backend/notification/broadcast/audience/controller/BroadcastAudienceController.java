package com.red_escuela.backend.notification.broadcast.audience.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.BroadcastAudiencePreviewResponse;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCatalogEntry;
import com.red_escuela.backend.notification.broadcast.audience.usecase.ListSegmentCatalogUseCase;
import com.red_escuela.backend.notification.broadcast.audience.usecase.PreviewBroadcastAudienceUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/broadcasts")
public class BroadcastAudienceController {

    private final ListSegmentCatalogUseCase listSegmentCatalog;
    private final PreviewBroadcastAudienceUseCase previewBroadcastAudience;

    public BroadcastAudienceController(ListSegmentCatalogUseCase listSegmentCatalog,
                                       PreviewBroadcastAudienceUseCase previewBroadcastAudience) {
        this.listSegmentCatalog = listSegmentCatalog;
        this.previewBroadcastAudience = previewBroadcastAudience;
    }

    @RequiresPermission(code = "notification.compose_broadcast", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @GetMapping("/segment-catalog")
    public List<SegmentCatalogEntry> listSegmentCatalog() {
        return listSegmentCatalog.listSegmentCatalog();
    }

    @RequiresPermission(code = "notification.compose_broadcast", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    @PostMapping("/audience-preview")
    public BroadcastAudiencePreviewResponse previewBroadcastAudience(@Valid @RequestBody AudiencePreviewRequest request) {
        return previewBroadcastAudience.previewBroadcastAudience(request);
    }
}
