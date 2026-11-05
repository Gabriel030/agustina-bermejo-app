package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import org.springframework.stereotype.Service;

@Service
public class InstitutionNameResolver {
    private static final String DEFAULT_NAME = "Red Escuela";

    private final InstitutionRepository institutionRepository;

    public InstitutionNameResolver(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    public String resolveInstitutionName() {
        return institutionRepository.findAll().stream().findFirst().map(Institution::getName).orElse(DEFAULT_NAME);
    }
}
