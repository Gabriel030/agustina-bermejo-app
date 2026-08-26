package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AssignElectiveGroupRequest;
import com.red_escuela.backend.dto.CreateElectiveGroupRequest;
import com.red_escuela.backend.dto.ElectiveGroupResponse;
import com.red_escuela.backend.dto.ProgramElectiveGroupResponse;
import com.red_escuela.backend.dto.UpdateElectiveGroupRequest;
import com.red_escuela.backend.service.ElectiveGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ElectiveGroupResponse crearGrupo(@Valid @RequestBody CreateElectiveGroupRequest request) {
        return ElectiveGroupResponse.from(electiveGroupService.crearGrupo(request));
    }

    @PutMapping("/elective-groups/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ElectiveGroupResponse editarGrupo(@PathVariable Long id, @RequestBody UpdateElectiveGroupRequest request) {
        return ElectiveGroupResponse.from(electiveGroupService.editarGrupo(id, request));
    }

    @GetMapping("/elective-groups")
    public List<ElectiveGroupResponse> listar() {
        return electiveGroupService.listar().stream().map(ElectiveGroupResponse::from).toList();
    }

    @GetMapping("/elective-groups/{id}")
    public ElectiveGroupResponse verDetalle(@PathVariable Long id) {
        return ElectiveGroupResponse.from(electiveGroupService.verDetalle(id));
    }

    @GetMapping("/programs/{id}/elective-groups")
    public List<ProgramElectiveGroupResponse> listarPorCarrera(@PathVariable Long id) {
        return electiveGroupService.listarPorCarrera(id).stream().map(ProgramElectiveGroupResponse::from).toList();
    }

    @PostMapping("/programs/{id}/elective-groups")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramElectiveGroupResponse asignarACarrera(@PathVariable Long id, @Valid @RequestBody AssignElectiveGroupRequest request) {
        return ProgramElectiveGroupResponse.from(electiveGroupService.asignarACarrera(id, request));
    }

    @DeleteMapping("/programs/{id}/elective-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void quitarDeCarrera(@PathVariable Long id, @PathVariable Long groupId) {
        electiveGroupService.quitarDeCarrera(id, groupId);
    }
}
