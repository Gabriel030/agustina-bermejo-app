package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.NewsletterPreferenceResponse;
import com.red_escuela.backend.identity.specialist.NewsletterPreferenceHandler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetNewsletterPreferenceService implements GetNewsletterPreferenceUseCase {

    private final NewsletterPreferenceHandler newsletterPreferenceHandler;

    public GetNewsletterPreferenceService(NewsletterPreferenceHandler newsletterPreferenceHandler) {
        this.newsletterPreferenceHandler = newsletterPreferenceHandler;
    }

    @Override
    public NewsletterPreferenceResponse getNewsletterPreference() {
        return newsletterPreferenceHandler.getForCurrentPerson();
    }
}
