package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.NewsletterPreferenceResponse;
import com.red_escuela.backend.identity.entity.NewsletterPreference;
import com.red_escuela.backend.identity.repository.NewsletterPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class NewsletterPreferenceHandler {

    private final CurrentPersonService currentPersonService;
    private final NewsletterPreferenceRepository newsletterPreferenceRepository;
    private final Clock clock;

    public NewsletterPreferenceHandler(CurrentPersonService currentPersonService,
                                       NewsletterPreferenceRepository newsletterPreferenceRepository, Clock clock) {
        this.currentPersonService = currentPersonService;
        this.newsletterPreferenceRepository = newsletterPreferenceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NewsletterPreferenceResponse getForCurrentPerson() {
        Long personId = currentPersonService.resolveCurrentPerson().getId();
        return newsletterPreferenceRepository.findById(personId)
                .map(this::toResponse)
                .orElseGet(() -> new NewsletterPreferenceResponse(false, null));
    }

    @Transactional
    public NewsletterPreferenceResponse updateForCurrentPerson(boolean optedIn) {
        Long personId = currentPersonService.resolveCurrentPerson().getId();
        LocalDateTime now = LocalDateTime.now(clock);
        newsletterPreferenceRepository.upsert(personId, optedIn, now);
        return new NewsletterPreferenceResponse(optedIn, now);
    }

    private NewsletterPreferenceResponse toResponse(NewsletterPreference preference) {
        return new NewsletterPreferenceResponse(preference.isOptedIn(), preference.getUpdatedAt());
    }
}
