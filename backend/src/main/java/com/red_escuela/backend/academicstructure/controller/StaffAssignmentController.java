package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.StaffAssignmentsResponse;
import com.red_escuela.backend.academicstructure.usecase.staffassignment.ListStaffAssignmentsUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffAssignmentController {

    private final ListStaffAssignmentsUseCase listStaffAssignmentsUseCase;

    public StaffAssignmentController(ListStaffAssignmentsUseCase listStaffAssignmentsUseCase) {
        this.listStaffAssignmentsUseCase = listStaffAssignmentsUseCase;
    }

    @GetMapping("/staff/{id}/assignments")
    @PreAuthorize("hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR') or hasRole('DOCENTE')")
    public StaffAssignmentsResponse list(@PathVariable Long id,
                                         @RequestParam(defaultValue = "false") boolean includeClosed) {
        return listStaffAssignmentsUseCase.list(id, includeClosed);
    }
}
