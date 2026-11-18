package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.NewsletterPreference;
import com.red_escuela.backend.identity.repository.NewsletterPreferenceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NewsletterPreferenceFinder {

    private final NewsletterPreferenceRepository newsletterPreferenceRepository;

    public NewsletterPreferenceFinder(NewsletterPreferenceRepository newsletterPreferenceRepository) {
        this.newsletterPreferenceRepository = newsletterPreferenceRepository;
    }

    @Transactional(readOnly = true)
    public boolean isOptedIn(Long personId) {
        return newsletterPreferenceRepository.findById(personId).map(NewsletterPreference::isOptedIn).orElse(false);
    }

    @Transactional(readOnly = true)
    public Set<Long> findOptedInPersonIds(Collection<Long> personIds) {
        return newsletterPreferenceRepository.findAllById(personIds).stream()
                .filter(NewsletterPreference::isOptedIn)
                .map(NewsletterPreference::getPersonId)
                .collect(Collectors.toSet());
    }
}
