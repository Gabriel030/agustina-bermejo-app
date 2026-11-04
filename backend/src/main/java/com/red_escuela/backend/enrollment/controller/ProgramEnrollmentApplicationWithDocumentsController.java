package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.ApplicationDocumentFiles;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.SubmitProgramEnrollmentApplicationWithDocumentsCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.SubmitProgramEnrollmentApplicationWithDocumentsUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProgramEnrollmentApplicationWithDocumentsController {
    private final CurrentStudentService currentStudentService;
    private final SubmitProgramEnrollmentApplicationWithDocumentsUseCase submitUseCase;

    public ProgramEnrollmentApplicationWithDocumentsController(CurrentStudentService currentStudentService,
                                                               SubmitProgramEnrollmentApplicationWithDocumentsUseCase submitUseCase) {
        this.currentStudentService = currentStudentService;
        this.submitUseCase = submitUseCase;
    }

    @PostMapping(value = "/me/program-enrollment-applications/with-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramEnrollmentApplicationResponse requestWithDocuments(
            @RequestParam Long programId, @RequestParam Long cohortId,
            @RequestPart(required = false) MultipartFile dniFront,
            @RequestPart(required = false) MultipartFile dniBack,
            @RequestPart(required = false) MultipartFile academicTranscript) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return submitUseCase.submit(new SubmitProgramEnrollmentApplicationWithDocumentsCommand(
                studentId, programId, cohortId, new ApplicationDocumentFiles(dniFront, dniBack, academicTranscript)));
    }
}
