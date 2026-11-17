package com.red_escuela.backend.notification.broadcast.audience.usecase;

import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewResponse;

public interface PreviewBroadcastAudienceUseCase {
    AudiencePreviewResponse previewBroadcastAudience(AudiencePreviewRequest request);
}
