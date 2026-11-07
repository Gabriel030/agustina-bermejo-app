package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

@Service
public class CurrentAnnouncementAuthorFinder {

    private final CurrentActorFinder currentActorFinder;

    public CurrentAnnouncementAuthorFinder(CurrentActorFinder currentActorFinder) {
        this.currentActorFinder = currentActorFinder;
    }

    public Person currentAuthor() {
        return currentActorFinder.currentPerson();
    }
}
