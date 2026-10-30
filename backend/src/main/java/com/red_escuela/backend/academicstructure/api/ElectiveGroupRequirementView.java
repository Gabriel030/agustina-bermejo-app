package com.red_escuela.backend.academicstructure.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ElectiveGroupRequirementView(Long electiveGroupId, String name, Integer minRequired,
                                           Integer maxAllowed) {
}
