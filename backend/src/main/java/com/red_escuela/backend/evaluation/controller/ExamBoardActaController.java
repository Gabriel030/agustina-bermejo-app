package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.dto.ExamBoardActaFile;
import com.red_escuela.backend.evaluation.usecase.examboard.GenerateExamBoardActaCommand;
import com.red_escuela.backend.evaluation.usecase.examboard.GenerateExamBoardActaUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
public class ExamBoardActaController {

    private static final Set<String> STAFF_WITH_FULL_ACCESS = Set.of("ROLE_PRECEPTOR", "ROLE_ADMINISTRATIVO", "ROLE_DIRECTOR");

    private final CurrentInstructorService currentInstructorService;
    private final GenerateExamBoardActaUseCase generateActaUseCase;

    public ExamBoardActaController(CurrentInstructorService currentInstructorService, GenerateExamBoardActaUseCase generateActaUseCase) {
        this.currentInstructorService = currentInstructorService;
        this.generateActaUseCase = generateActaUseCase;
    }

    @GetMapping("/exam-boards/{id}/acta")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @RequiresPermission(code = "evaluation.read_exam_boards", legacyRoles = "ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR,ROLE_DOCENTE")
    public ResponseEntity<byte[]> download(@PathVariable Long id, Authentication authentication) {
        Long tribunalInstructorId = isTeacherOnly(authentication) ? currentInstructorService.resolveCurrentInstructor().getId() : null;
        ExamBoardActaFile acta = generateActaUseCase.generate(new GenerateExamBoardActaCommand(id, tribunalInstructorId));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(acta.fileName()).build().toString())
                .body(acta.content());
    }

    private boolean isTeacherOnly(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(STAFF_WITH_FULL_ACCESS::contains);
    }
}
