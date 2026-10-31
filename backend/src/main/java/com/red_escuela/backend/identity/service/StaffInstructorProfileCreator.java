package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.InstructorProfileCreationPort;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;

@Service
public class StaffInstructorProfileCreator {

    private final InstructorProfileCreationPort instructorProfileCreationPort;

    public StaffInstructorProfileCreator(InstructorProfileCreationPort instructorProfileCreationPort) {
        this.instructorProfileCreationPort = instructorProfileCreationPort;
    }

    public void createInstructorProfileFor(Person person) {
        instructorProfileCreationPort.createInstructorProfile(person);
    }
}
