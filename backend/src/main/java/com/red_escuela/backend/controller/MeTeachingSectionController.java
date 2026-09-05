package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.TeachingSectionResponse;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.service.CurrentInstructorService;
import com.red_escuela.backend.service.TeachingSectionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeTeachingSectionController {

    private final TeachingSectionService teachingSectionService;
    private final CurrentInstructorService currentInstructorService;

    public MeTeachingSectionController(TeachingSectionService teachingSectionService, CurrentInstructorService currentInstructorService) {
        this.teachingSectionService = teachingSectionService;
        this.currentInstructorService = currentInstructorService;
    }

    @GetMapping("/me/teaching-sections")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<TeachingSectionResponse> mySections() {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return teachingSectionService.listByInstructor(instructor.getId());
    }
}
