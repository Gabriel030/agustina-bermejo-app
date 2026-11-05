package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;

@Service
public class AnnouncementViewer {

    private final CurrentActorFinder currentActorFinder;

    public AnnouncementViewer(CurrentActorFinder currentActorFinder) {
        this.currentActorFinder = currentActorFinder;
    }

    public Long currentPersonId() {
        return currentActorFinder.currentPerson().getId();
    }
}
