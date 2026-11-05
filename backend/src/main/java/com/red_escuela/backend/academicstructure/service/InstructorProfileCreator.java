package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.identity.api.InstructorProfileCreationPort;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructorProfileCreator implements InstructorProfileCreationPort {

    private final InstructorRepository instructorRepository;

    public InstructorProfileCreator(InstructorRepository instructorRepository) {
        this.instructorRepository = instructorRepository;
    }

    @Override
    @Transactional
    public void createInstructorProfile(Person person) {
        if (instructorRepository.findByPerson_Id(person.getId()).isPresent()) {
            return;
        }
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        instructorRepository.save(instructor);
    }
}
