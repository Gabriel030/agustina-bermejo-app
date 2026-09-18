package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.InstructorSummaryResponse;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InstructorController {

    private final InstructorRepository instructorRepository;

    public InstructorController(InstructorRepository instructorRepository) {
        this.instructorRepository = instructorRepository;
    }

    @GetMapping("/instructors")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<InstructorSummaryResponse> list() {
        return instructorRepository.findAll().stream().map(InstructorSummaryResponse::from).toList();
    }
}
