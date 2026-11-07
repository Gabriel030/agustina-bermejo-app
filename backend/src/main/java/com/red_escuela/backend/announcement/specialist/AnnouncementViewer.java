package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.StudentFinder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AnnouncementViewer {

    private final CurrentActorFinder currentActorFinder;

    private final StudentFinder studentFinder;

    public AnnouncementViewer(CurrentActorFinder currentActorFinder, StudentFinder studentFinder) {
        this.currentActorFinder = currentActorFinder;
        this.studentFinder = studentFinder;
    }

    public Optional<Long> currentStudentId() {
        return studentFinder.findIdByPersonId(currentPersonId());
    }

    public Long currentPersonId() {
        return currentActorFinder.currentPerson().getId();
    }
}
