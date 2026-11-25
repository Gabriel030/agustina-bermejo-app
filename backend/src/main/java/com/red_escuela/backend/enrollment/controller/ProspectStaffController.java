package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.dto.ProspectListItemResponse;
import com.red_escuela.backend.enrollment.dto.ProspectResponse;
import com.red_escuela.backend.enrollment.usecase.prospect.CreateProspectByStaffUseCase;
import com.red_escuela.backend.enrollment.usecase.prospect.ListProspectsUseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/prospects")
public class ProspectStaffController {

    private final CreateProspectByStaffUseCase createProspectByStaff;
    private final ListProspectsUseCase listProspects;

    public ProspectStaffController(CreateProspectByStaffUseCase createProspectByStaff, ListProspectsUseCase listProspects) {
        this.createProspectByStaff = createProspectByStaff;
        this.listProspects = listProspects;
    }

    @PostMapping("/by-staff")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "enrollment.manage_prospects", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProspectResponse createProspectByStaff(@Valid @RequestBody CreateProspectRequest request) {
        return createProspectByStaff.create(request);
    }

    @GetMapping
    @RequiresPermission(code = "enrollment.read_prospects", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public PageResponse<ProspectListItemResponse> listProspects(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return listProspects.list(search, page, size);
    }
}
