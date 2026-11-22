package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.StaffAssignmentsResponse;
import com.red_escuela.backend.academicstructure.usecase.staffassignment.ListStaffAssignmentsUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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
    @RequiresPermission(code = "academicstructure.read_staff_assignments", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR,ROLE_DOCENTE")
    public StaffAssignmentsResponse list(@PathVariable Long id,
                                         @RequestParam(defaultValue = "false") boolean includeClosed) {
        return listStaffAssignmentsUseCase.list(id, includeClosed);
    }
}
