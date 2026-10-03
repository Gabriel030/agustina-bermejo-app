package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.service.FileStorageService;
import com.red_escuela.backend.service.TransactionBoundFileCleanup;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class AddProgramEnrollmentApplicationDocumentService implements AddProgramEnrollmentApplicationDocumentUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final StudentDocumentRepository studentDocumentRepository;
    private final FileStorageService fileStorageService;
    private final TransactionBoundFileCleanup transactionBoundFileCleanup;

    public AddProgramEnrollmentApplicationDocumentService(ProgramEnrollmentApplicationFinder applicationFinder,
                                                            StudentDocumentRepository studentDocumentRepository,
                                                            FileStorageService fileStorageService, TransactionBoundFileCleanup transactionBoundFileCleanup) {
        this.applicationFinder = applicationFinder;
        this.studentDocumentRepository = studentDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.transactionBoundFileCleanup = transactionBoundFileCleanup;
    }

    @Override
    @Transactional
    public StudentDocument add(AddProgramEnrollmentApplicationDocumentCommand command) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(command.applicationId());
        Long studentId = application.getStudent().getId();

        studentDocumentRepository.findByStudentIdAndType(studentId, command.type()).ifPresent(existing -> {
            transactionBoundFileCleanup.deleteAfterCommit(existing.getFilePath());
            studentDocumentRepository.delete(existing);
        });

        String relativePath = fileStorageService.store(studentId, command.type().name(), command.file());
        transactionBoundFileCleanup.deleteIfRolledBack(relativePath);

        StudentDocument document = new StudentDocument();
        document.setStudent(application.getStudent());
        document.setType(command.type());
        document.setFilePath(relativePath);
        document.setUploadedAt(LocalDateTime.now());
        return studentDocumentRepository.save(document);
    }
}
