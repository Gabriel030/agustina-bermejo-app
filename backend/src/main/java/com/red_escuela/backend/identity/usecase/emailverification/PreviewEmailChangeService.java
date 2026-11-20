package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.dto.EmailChangePreviewResponse;
import com.red_escuela.backend.identity.service.EmailChangeReviewer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewEmailChangeService implements PreviewEmailChangeUseCase {

    private final EmailChangeReviewer emailChangeReviewer;

    public PreviewEmailChangeService(EmailChangeReviewer emailChangeReviewer) {
        this.emailChangeReviewer = emailChangeReviewer;
    }

    @Override
    public EmailChangePreviewResponse previewForOwner(String token) {
        return emailChangeReviewer.preview(token);
    }
}
