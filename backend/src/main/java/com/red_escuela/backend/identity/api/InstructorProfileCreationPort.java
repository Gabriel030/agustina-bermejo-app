package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;

public interface InstructorProfileCreationPort {
    void createInstructorProfile(Person person);
}
