package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.PublishedGradesSummary;
import com.red_escuela.backend.evaluation.usecase.gradepublication.PublishSectionGradeItemCommand;
import com.red_escuela.backend.evaluation.usecase.gradepublication.PublishSectionGradeItemUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GradePublicationController {

    private final PublishSectionGradeItemUseCase publishSectionGradeItemUseCase;
    private final CurrentInstructorService currentInstructorService;

    public GradePublicationController(PublishSectionGradeItemUseCase publishSectionGradeItemUseCase,
                                      CurrentInstructorService currentInstructorService) {
        this.publishSectionGradeItemUseCase = publishSectionGradeItemUseCase;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/program-sections/{id}/grade-items/publish")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('DOCENTE')")
    public PublishedGradesSummary publishProgramSectionGradeItem(
            @PathVariable Long id, @Valid @RequestBody PublishGradeItemRequest request) {
        return publish(SectionKind.PROGRAM, id, request);
    }

    @PostMapping("/course-sections/{id}/grade-items/publish")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('DOCENTE')")
    public PublishedGradesSummary publishCourseSectionGradeItem(
            @PathVariable Long id, @Valid @RequestBody PublishGradeItemRequest request) {
        return publish(SectionKind.COURSE, id, request);
    }

    private PublishedGradesSummary publish(SectionKind kind, Long sectionId, PublishGradeItemRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return publishSectionGradeItemUseCase.publish(new PublishSectionGradeItemCommand(
                kind, sectionId, instructor.getId(), instructor.getPerson().getId(),
                request.gradeType(), request.sequenceNumber()));
    }
}
