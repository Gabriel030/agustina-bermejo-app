package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

@Service
public class CurrentAnnouncementAuthor {

    private final CurrentActorFinder currentActorFinder;

    public CurrentAnnouncementAuthor(CurrentActorFinder currentActorFinder) {
        this.currentActorFinder = currentActorFinder;
    }

    public Person currentAuthor() {
        return currentActorFinder.currentPerson();
    }
}
