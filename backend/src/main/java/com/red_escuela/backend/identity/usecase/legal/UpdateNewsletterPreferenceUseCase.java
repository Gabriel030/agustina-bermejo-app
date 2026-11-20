package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.NewsletterPreferenceResponse;

public interface UpdateNewsletterPreferenceUseCase {
    NewsletterPreferenceResponse updateNewsletterPreference(boolean optedIn);
}
