package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.service.FileStorageService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class AddProgramEnrollmentApplicationDocumentService implements AddProgramEnrollmentApplicationDocumentUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;
    private final StudentDocumentRepository studentDocumentRepository;
    private final FileStorageService fileStorageService;

    public AddProgramEnrollmentApplicationDocumentService(ProgramEnrollmentApplicationFinder applicationFinder,
                                                            StudentDocumentRepository studentDocumentRepository,
                                                            FileStorageService fileStorageService) {
        this.applicationFinder = applicationFinder;
        this.studentDocumentRepository = studentDocumentRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional
    public StudentDocument add(AddProgramEnrollmentApplicationDocumentCommand command) {
        ProgramEnrollmentApplication application = applicationFinder.findOrThrow(command.applicationId());
        Long studentId = application.getStudent().getId();

        studentDocumentRepository.findByStudentIdAndType(studentId, command.type()).ifPresent(existing -> {
            fileStorageService.delete(existing.getFilePath());
            studentDocumentRepository.delete(existing);
        });

        String relativePath = fileStorageService.store(studentId, command.type().name(), command.file());

        StudentDocument document = new StudentDocument();
        document.setStudent(application.getStudent());
        document.setType(command.type());
        document.setFilePath(relativePath);
        document.setUploadedAt(LocalDateTime.now());
        return studentDocumentRepository.save(document);
    }
}
