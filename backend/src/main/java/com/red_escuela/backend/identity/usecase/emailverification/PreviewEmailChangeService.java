package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.dto.EmailChangePreviewResponse;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;

@UseCase
public class PreviewEmailChangeService implements PreviewEmailChangeUseCase {

    @Override
    public EmailChangePreviewResponse previewForOwner(String token) {
        throw new InvalidEmailVerificationTokenException();
    }
}
