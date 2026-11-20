package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.NewsletterPreferenceResponse;
import com.red_escuela.backend.identity.specialist.NewsletterPreferenceHandler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateNewsletterPreferenceService implements UpdateNewsletterPreferenceUseCase {

    private final NewsletterPreferenceHandler newsletterPreferenceHandler;

    public UpdateNewsletterPreferenceService(NewsletterPreferenceHandler newsletterPreferenceHandler) {
        this.newsletterPreferenceHandler = newsletterPreferenceHandler;
    }

    @Override
    public NewsletterPreferenceResponse updateNewsletterPreference(boolean optedIn) {
        return newsletterPreferenceHandler.updateForCurrentPerson(optedIn);
    }
}
