package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.dto.StaffResponse;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/staff")
public class StaffController {

    // Administrativo (Docente/Preceptor) y Director (Administrativo) según RF-AD-05/RF-DI-04
    // — el @PreAuthorize solo exige "alguno de los dos", la regla de "cuál rol exacto" vive
    // en StaffService.requireCanAssign.
    private static final Set<String> STAFF_ROLES = Set.of("DOCENTE", "PRECEPTOR", "ADMINISTRATIVO");

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StaffResponse createStaff(@Valid @RequestBody CreateStaffRequest request) {
        Person person = staffService.createStaff(request);
        return StaffResponse.from(person, request.role());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StaffResponse updateStaff(@PathVariable Long id, @Valid @RequestBody UpdateStaffRequest request) {
        Person person = staffService.updateStaff(id, request);
        return StaffResponse.from(person, resolveStaffRole(person));
    }

    private String resolveStaffRole(Person person) {
        return person.getRoles().stream()
                .map(Role::getName)
                .filter(STAFF_ROLES::contains)
                .findFirst()
                .orElse(null);
    }
}
