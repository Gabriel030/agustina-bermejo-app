package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.dto.StaffResponse;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.usecase.staff.CreateStaffUseCase;
import com.red_escuela.backend.identity.usecase.staff.StaffUseCaseResult;
import com.red_escuela.backend.identity.usecase.staff.UpdateStaffCommand;
import com.red_escuela.backend.identity.usecase.staff.UpdateStaffUseCase;
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

@RestController
@RequestMapping("/staff")
public class StaffController {

    private final CreateStaffUseCase createStaffUseCase;
    private final UpdateStaffUseCase updateStaffUseCase;

    public StaffController(CreateStaffUseCase createStaffUseCase, UpdateStaffUseCase updateStaffUseCase) {
        this.createStaffUseCase = createStaffUseCase;
        this.updateStaffUseCase = updateStaffUseCase;
    }

    // Administrativo (Docente/Preceptor) y Director (Administrativo) según RF-AD-05/RF-DI-04
    // — el @PreAuthorize solo exige "alguno de los dos", la regla de "cuál rol exacto" vive
    // en StaffRoleAssignmentPolicy.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StaffResponse createStaff(@Valid @RequestBody CreateStaffRequest request) {
        StaffUseCaseResult result = createStaffUseCase.create(request);
        return StaffResponse.from(result.person(), result.role());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StaffResponse updateStaff(@PathVariable Long id, @Valid @RequestBody UpdateStaffRequest request) {
        StaffUseCaseResult result = updateStaffUseCase.update(new UpdateStaffCommand(id, request));
        return StaffResponse.from(result.person(), result.role());
    }
}
