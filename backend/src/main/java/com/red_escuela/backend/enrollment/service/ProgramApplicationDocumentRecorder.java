package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.TransactionBoundFileCleanup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ProgramApplicationDocumentRecorder {

    private final StudentDocumentRepository studentDocumentRepository;
    private final FileStorageService fileStorageService;
    private final TransactionBoundFileCleanup transactionBoundFileCleanup;
    private final Clock clock;

    public ProgramApplicationDocumentRecorder(StudentDocumentRepository studentDocumentRepository,
                                              FileStorageService fileStorageService,
                                              TransactionBoundFileCleanup transactionBoundFileCleanup,
                                              Clock clock) {
        this.studentDocumentRepository = studentDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.transactionBoundFileCleanup = transactionBoundFileCleanup;
        this.clock = clock;
    }

    @Transactional
    public StudentDocument record(ProgramEnrollmentApplication application, DocumentType type, MultipartFile file) {
        Long studentId = application.getStudent().getId();

        studentDocumentRepository.findByStudentIdAndType(studentId, type).ifPresent(existing -> {
            transactionBoundFileCleanup.deleteAfterCommit(existing.getFilePath());
            studentDocumentRepository.delete(existing);
        });

        String relativePath = fileStorageService.store(studentId, type.name(), file);
        transactionBoundFileCleanup.deleteIfRolledBack(relativePath);

        StudentDocument document = new StudentDocument();
        document.setStudent(application.getStudent());
        document.setType(type);
        document.setFilePath(relativePath);
        document.setUploadedAt(LocalDateTime.now(clock));
        return studentDocumentRepository.save(document);
    }
}
