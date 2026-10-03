package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.dto.CreateAcademicOfferingCycleRequest;
import com.red_escuela.backend.academicstructure.dto.CreateCycleSectionRequest;
import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse;
import com.red_escuela.backend.academicstructure.dto.ModifySectionRequest;
import com.red_escuela.backend.academicstructure.dto.RejectCycleRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.usecase.cycle.ApproveCycleUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.ApproveSectionModificationUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.CreateAcademicOfferingCycleUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.CreateCycleSectionUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.GetAcademicOfferingCycleUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.GetCycleSectionsUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.ListAcademicOfferingCyclesUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.RejectCycleUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.RejectSectionModificationUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.RequestSectionModificationUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.SubmitCycleForApprovalUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AcademicOfferingCycleController {

    private final CreateAcademicOfferingCycleUseCase createCycle;
    private final ListAcademicOfferingCyclesUseCase listCycles;
    private final CreateCycleSectionUseCase createSection;
    private final SubmitCycleForApprovalUseCase submitCycle;
    private final ApproveCycleUseCase approveCycle;
    private final RejectCycleUseCase rejectCycle;
    private final GetCycleSectionsUseCase getCycleSections;
    private final RequestSectionModificationUseCase requestModification;
    private final ApproveSectionModificationUseCase approveModification;
    private final RejectSectionModificationUseCase rejectModification;
    private final GetAcademicOfferingCycleUseCase getCycle;

    public AcademicOfferingCycleController(
            CreateAcademicOfferingCycleUseCase createCycle,
            ListAcademicOfferingCyclesUseCase listCycles,
            CreateCycleSectionUseCase createSection,
            SubmitCycleForApprovalUseCase submitCycle,
            ApproveCycleUseCase approveCycle,
            RejectCycleUseCase rejectCycle,
            GetCycleSectionsUseCase getCycleSections,
            RequestSectionModificationUseCase requestModification,
            ApproveSectionModificationUseCase approveModification,
            RejectSectionModificationUseCase rejectModification,
            GetAcademicOfferingCycleUseCase getCycle
    ) {
        this.createCycle = createCycle;
        this.listCycles = listCycles;
        this.createSection = createSection;
        this.submitCycle = submitCycle;
        this.approveCycle = approveCycle;
        this.rejectCycle = rejectCycle;
        this.getCycleSections = getCycleSections;
        this.requestModification = requestModification;
        this.approveModification = approveModification;
        this.rejectModification = rejectModification;
        this.getCycle = getCycle;
    }

    @PostMapping("/academic-offering-cycles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AcademicOfferingCycleResponse create(@Valid @RequestBody CreateAcademicOfferingCycleRequest request) {
        return createCycle.create(request);
    }

    @GetMapping("/academic-offering-cycles")
    @PreAuthorize("isAuthenticated()")
    public List<AcademicOfferingCycleResponse> list(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) AcademicOfferingCycleStatus status
    ) {
        return listCycles.list(programId, courseId, status);
    }

    @GetMapping("/academic-offering-cycles/{id}")
    @PreAuthorize("isAuthenticated()")
    public AcademicOfferingCycleResponse get(@PathVariable Long id) {
        return getCycle.get(id);
    }

    @PostMapping("/academic-offering-cycles/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public Long createSection(@PathVariable Long id, @Valid @RequestBody CreateCycleSectionRequest request) {
        return createSection.createSection(id, request);
    }

    @PostMapping("/academic-offering-cycles/{id}/submit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void submit(@PathVariable Long id) {
        submitCycle.submit(id);
    }

    @PostMapping("/academic-offering-cycles/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DIRECTOR')")
    public void approve(@PathVariable Long id) {
        approveCycle.approve(id);
    }

    @PostMapping("/academic-offering-cycles/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DIRECTOR')")
    public void reject(@PathVariable Long id, @Valid @RequestBody RejectCycleRequest request) {
        rejectCycle.reject(id, request.reason());
    }

    @GetMapping("/academic-offering-cycles/{id}/sections")
    @PreAuthorize("isAuthenticated()")
    public List<CycleSectionResponse> getSections(@PathVariable Long id) {
        return getCycleSections.getSections(id);
    }

    @PostMapping("/subject-sections/{id}/modification-request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void requestSectionModification(@PathVariable Long id, @Valid @RequestBody ModifySectionRequest request) {
        requestModification.requestModification(id, request);
    }

    @PostMapping("/subject-sections/{id}/modification-request/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DIRECTOR')")
    public void approveSectionModification(@PathVariable Long id) {
        approveModification.approveModification(id);
    }

    @PostMapping("/subject-sections/{id}/modification-request/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DIRECTOR')")
    public void rejectSectionModification(@PathVariable Long id, @Valid @RequestBody RejectCycleRequest request) {
        rejectModification.rejectModification(id, request.reason());
    }
}
