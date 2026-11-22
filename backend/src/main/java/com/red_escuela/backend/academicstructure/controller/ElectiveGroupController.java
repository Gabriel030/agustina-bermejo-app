package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.AssignElectiveGroupRequest;
import com.red_escuela.backend.academicstructure.dto.CreateElectiveGroupRequest;
import com.red_escuela.backend.academicstructure.dto.ElectiveGroupResponse;
import com.red_escuela.backend.academicstructure.dto.ProgramElectiveGroupResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateElectiveGroupRequest;
import com.red_escuela.backend.academicstructure.service.ElectiveGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ElectiveGroupController {

    private final ElectiveGroupService electiveGroupService;

    public ElectiveGroupController(ElectiveGroupService electiveGroupService) {
        this.electiveGroupService = electiveGroupService;
    }

    @PostMapping("/elective-groups")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "academicstructure.manage_programs", legacyRoles = "ROLE_ADMINISTRATIVO")
    public ElectiveGroupResponse create(@Valid @RequestBody CreateElectiveGroupRequest request) {
        return ElectiveGroupResponse.from(electiveGroupService.create(request));
    }

    @PutMapping("/elective-groups/{id}")
    @RequiresPermission(code = "academicstructure.manage_programs", legacyRoles = "ROLE_ADMINISTRATIVO")
    public ElectiveGroupResponse update(@PathVariable Long id, @RequestBody UpdateElectiveGroupRequest request) {
        return ElectiveGroupResponse.from(electiveGroupService.update(id, request));
    }

    @GetMapping("/elective-groups")
    public List<ElectiveGroupResponse> list() {
        return electiveGroupService.list().stream().map(ElectiveGroupResponse::from).toList();
    }

    @GetMapping("/elective-groups/{id}")
    public ElectiveGroupResponse get(@PathVariable Long id) {
        return ElectiveGroupResponse.from(electiveGroupService.get(id));
    }

    @GetMapping("/programs/{id}/elective-groups")
    public List<ProgramElectiveGroupResponse> listByProgram(@PathVariable Long id) {
        return electiveGroupService.listByProgram(id).stream().map(ProgramElectiveGroupResponse::from).toList();
    }

    @PostMapping("/programs/{id}/elective-groups")
    @RequiresPermission(code = "academicstructure.manage_programs", legacyRoles = "ROLE_ADMINISTRATIVO")
    public ProgramElectiveGroupResponse assignToProgram(@PathVariable Long id, @Valid @RequestBody AssignElectiveGroupRequest request) {
        return ProgramElectiveGroupResponse.from(electiveGroupService.assignToProgram(id, request));
    }

    @DeleteMapping("/programs/{id}/elective-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.manage_programs", legacyRoles = "ROLE_ADMINISTRATIVO")
    public void removeFromProgram(@PathVariable Long id, @PathVariable Long groupId) {
        electiveGroupService.removeFromProgram(id, groupId);
    }
}
