package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

@Service
public class AttendanceActorResolver {

    private final CurrentActorFinder currentActorFinder;

    public AttendanceActorResolver(CurrentActorFinder currentActorFinder) {
        this.currentActorFinder = currentActorFinder;
    }

    public Person currentActor() {
        return currentActorFinder.currentPerson();
    }
}
