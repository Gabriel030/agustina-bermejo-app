package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.dto.ProspectListItemResponse;
import com.red_escuela.backend.enrollment.dto.ProspectResponse;
import com.red_escuela.backend.enrollment.usecase.prospect.CreateProspectByStaffUseCase;
import com.red_escuela.backend.enrollment.usecase.prospect.ListProspectsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ProspectResponse createProspectByStaff(@Valid @RequestBody CreateProspectRequest request) {
        return createProspectByStaff.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<ProspectListItemResponse> listProspects() {
        return listProspects.list();
    }
}
