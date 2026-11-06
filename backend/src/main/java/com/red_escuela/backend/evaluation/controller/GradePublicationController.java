package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.PublishedGradesSummary;
import com.red_escuela.backend.evaluation.usecase.gradepublication.PublishSectionGradeItemCommand;
import com.red_escuela.backend.evaluation.usecase.gradepublication.PublishCourseSectionGradeItemUseCase;
import com.red_escuela.backend.evaluation.usecase.gradepublication.PublishProgramSectionGradeItemUseCase;
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

    private final PublishProgramSectionGradeItemUseCase publishProgramSectionGradeItemUseCase;
    private final PublishCourseSectionGradeItemUseCase publishCourseSectionGradeItemUseCase;
    private final CurrentInstructorService currentInstructorService;

    public GradePublicationController(PublishProgramSectionGradeItemUseCase publishProgramSectionGradeItemUseCase,
                                      PublishCourseSectionGradeItemUseCase publishCourseSectionGradeItemUseCase,
                                      CurrentInstructorService currentInstructorService) {
        this.publishProgramSectionGradeItemUseCase = publishProgramSectionGradeItemUseCase;
        this.publishCourseSectionGradeItemUseCase = publishCourseSectionGradeItemUseCase;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/program-sections/{id}/grade-items/publish")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('DOCENTE')")
    public PublishedGradesSummary publishProgramSectionGradeItem(
            @PathVariable Long id, @Valid @RequestBody PublishGradeItemRequest request) {
        return publishProgramSectionGradeItemUseCase.publish(commandOf(id, request));
    }

    @PostMapping("/course-sections/{id}/grade-items/publish")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('DOCENTE')")
    public PublishedGradesSummary publishCourseSectionGradeItem(
            @PathVariable Long id, @Valid @RequestBody PublishGradeItemRequest request) {
        return publishCourseSectionGradeItemUseCase.publish(commandOf(id, request));
    }

    private PublishSectionGradeItemCommand commandOf(Long sectionId, PublishGradeItemRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return new PublishSectionGradeItemCommand(sectionId, instructor.getId(), instructor.getPerson().getId(), request);
    }
}
