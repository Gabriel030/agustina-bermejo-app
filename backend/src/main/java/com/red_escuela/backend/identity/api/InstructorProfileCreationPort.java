package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import java.util.Collection;

public interface InstructorProfileCreationPort {
    void createInstructorProfile(Person person);

    void removeInstructorProfile(Long personId);

    default void removeInstructorProfiles(Collection<Long> personIds) {
        personIds.forEach(this::removeInstructorProfile);
    }
}
