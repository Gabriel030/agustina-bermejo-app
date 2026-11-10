package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramApplicationDocumentFile;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.exception.ApplicationDocumentNotFoundException;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.StoredFileMediaTypes;

import java.nio.file.Path;

@UseCase
public class GetProgramEnrollmentApplicationDocumentService implements GetProgramEnrollmentApplicationDocumentUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final StudentDocumentRepository studentDocumentRepository;
    private final FileStorageService fileStorageService;

    public GetProgramEnrollmentApplicationDocumentService(ProgramEnrollmentApplicationFinder applicationFinder,
                                                          StudentDocumentRepository studentDocumentRepository,
                                                          FileStorageService fileStorageService) {
        this.applicationFinder = applicationFinder;
        this.studentDocumentRepository = studentDocumentRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public ProgramApplicationDocumentFile get(Long applicationId, Long documentId) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(applicationId);
        StudentDocument document = studentDocumentRepository.findById(documentId)
                .filter(candidate -> candidate.getStudent().getId().equals(application.getStudent().getId()))
                .filter(candidate -> fileStorageService.exists(candidate.getFilePath()))
                .orElseThrow(() -> new ApplicationDocumentNotFoundException(applicationId, documentId));
        String fileName = Path.of(document.getFilePath()).getFileName().toString();
        return new ProgramApplicationDocumentFile(fileName, StoredFileMediaTypes.of(fileName), fileStorageService.readBytes(document.getFilePath()));
    }
}
