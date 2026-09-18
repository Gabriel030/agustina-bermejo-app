package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.InstitutionResponse;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/institutions")
public class InstitutionController {
    private final InstitutionRepository institutionRepository;

    public InstitutionController(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    @GetMapping
    public List<InstitutionResponse> list() {
        return institutionRepository.findAll().stream().map(InstitutionResponse::from).toList();
    }
}
