package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.dto.EmailChangePreviewResponse;

public interface PreviewEmailChangeUseCase {

    EmailChangePreviewResponse previewForOwner(String token);
}
