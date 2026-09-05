package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.InstitutionResponse;
import com.red_escuela.backend.repository.InstitutionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Público (permitAll en SecurityConfig, junto a /auth/**): Registrarse.jsx lo
 * necesita antes de tener sesión, para resolver el institutionId que exige
 * RegisterRequest sin pedírselo al usuario (hoy el sistema es de una sola
 * institución por despliegue).
 */
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
