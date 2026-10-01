package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.AddCorequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.CorequisiteResponse;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.PrerequisiteResponse;
import com.red_escuela.backend.academicstructure.dto.ProgramSubjectResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramSubjectRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.service.ProgramSubjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProgramSubjectController {

    private final ProgramSubjectService programSubjectService;

    public ProgramSubjectController(ProgramSubjectService programSubjectService) {
        this.programSubjectService = programSubjectService;
    }

    @PostMapping("/programs/{id}/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramSubjectResponse addSubject(@PathVariable Long id, @Valid @RequestBody CreateProgramSubjectRequest request) {
        return ProgramSubjectResponse.from(programSubjectService.addSubject(id, request));
    }

    @PutMapping("/program-subjects/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramSubjectResponse update(@PathVariable Long id, @RequestBody UpdateProgramSubjectRequest request) {
        return ProgramSubjectResponse.from(programSubjectService.update(id, request));
    }

    @GetMapping("/programs/{id}/subjects")
    public List<ProgramSubjectResponse> listByProgram(@PathVariable Long id, @RequestParam(required = false) StatusType status) {
        return programSubjectService.listByProgram(id, status).stream().map(ProgramSubjectResponse::from).toList();
    }

    @GetMapping("/program-subjects/{id}")
    public ProgramSubjectResponse get(@PathVariable Long id) {
        return ProgramSubjectResponse.from(programSubjectService.get(id));
    }

    @PostMapping("/program-subjects/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        programSubjectService.deactivate(id);
    }

    @PostMapping("/program-subjects/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void reactivate(@PathVariable Long id) {
        programSubjectService.reactivate(id);
    }

    @PostMapping("/program-subjects/{id}/prerequisites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public PrerequisiteResponse addPrerequisite(@PathVariable Long id, @Valid @RequestBody AddPrerequisiteRequest request) {
        return PrerequisiteResponse.from(programSubjectService.addPrerequisite(id, request));
    }

    @GetMapping("/program-subjects/{id}/prerequisites")
    public List<PrerequisiteResponse> listPrerequisites(@PathVariable Long id) {
        return programSubjectService.listPrerequisites(id).stream().map(PrerequisiteResponse::from).toList();
    }

    @DeleteMapping("/program-subjects/prerequisites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void removePrerequisite(@PathVariable Long id) {
        programSubjectService.removePrerequisite(id);
    }

    @PostMapping("/program-subjects/{id}/corequisites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CorequisiteResponse addCorequisite(@PathVariable Long id, @Valid @RequestBody AddCorequisiteRequest request) {
        return CorequisiteResponse.from(programSubjectService.addCorequisite(id, request));
    }

    @GetMapping("/program-subjects/{id}/corequisites")
    public List<CorequisiteResponse> listCorequisites(@PathVariable Long id) {
        return programSubjectService.listCorequisites(id).stream().map(CorequisiteResponse::from).toList();
    }

    @DeleteMapping("/program-subjects/corequisites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void removeCorequisite(@PathVariable Long id) {
        programSubjectService.removeCorequisite(id);
    }
}
