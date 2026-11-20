package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.dto.RenderPreviewRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.RenderPreviewResponse;

public interface RenderTemplatePreviewUseCase {
    RenderPreviewResponse renderTemplatePreview(RenderPreviewRequest request);
}
