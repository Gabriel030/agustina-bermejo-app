package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ApplicationDocumentFiles;

public record SubmitProgramEnrollmentApplicationWithDocumentsCommand(Long studentId, Long programId, Long cohortId,
                                                                      ApplicationDocumentFiles files) {
}
