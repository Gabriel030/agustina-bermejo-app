package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InstitutionProvisioner {
    private final InstitutionRepository institutionRepository;

    public InstitutionProvisioner(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    @Transactional
    public ProvisionedInstitution provisionSingleInstitution(String name) {
        return institutionRepository.findAll().stream()
                .findFirst()
                .map(existing -> new ProvisionedInstitution(existing.getId(), false))
                .orElseGet(() -> {
                    Institution institution = new Institution();
                    institution.setName(name);
                    return new ProvisionedInstitution(institutionRepository.save(institution).getId(), true);
                });
    }
}
