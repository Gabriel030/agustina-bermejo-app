package com.red_escuela.backend.notification.broadcast.audience.usecase;

import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.BroadcastAudiencePreviewResponse;

public interface PreviewBroadcastAudienceUseCase {
    BroadcastAudiencePreviewResponse previewBroadcastAudience(AudiencePreviewRequest request);
}
