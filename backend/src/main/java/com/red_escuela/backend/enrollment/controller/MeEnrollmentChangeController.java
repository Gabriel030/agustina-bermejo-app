package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ChangeSectionCandidateResponse;
import com.red_escuela.backend.enrollment.usecase.ListOwnChangeSectionCandidates;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me")
public class MeEnrollmentChangeController {

    private final CurrentStudentService currentStudentService;
    private final ListOwnChangeSectionCandidates listCandidates;

    public MeEnrollmentChangeController(CurrentStudentService currentStudentService,
                                        ListOwnChangeSectionCandidates listCandidates) {
        this.currentStudentService = currentStudentService;
        this.listCandidates = listCandidates;
    }

    @GetMapping("/section-enrollments/{id}/change-section-candidates")
    public List<ChangeSectionCandidateResponse> changeSectionCandidates(@PathVariable Long id) {
        return listCandidates.list(currentStudentService.resolveCurrentStudent().getId(), id);
    }
}
