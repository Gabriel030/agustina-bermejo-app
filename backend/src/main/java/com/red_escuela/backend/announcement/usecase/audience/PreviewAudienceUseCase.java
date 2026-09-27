package com.red_escuela.backend.announcement.usecase.audience;

import com.red_escuela.backend.announcement.dto.AudiencePreviewResponse;

public interface PreviewAudienceUseCase {

    AudiencePreviewResponse preview(PreviewAudienceCommand command);
}
