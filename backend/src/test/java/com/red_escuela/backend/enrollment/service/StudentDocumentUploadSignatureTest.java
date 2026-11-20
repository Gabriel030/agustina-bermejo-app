package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.TransactionBoundFileCleanup;
import com.red_escuela.backend.shared.storage.UnsupportedFileTypeException;
import com.red_escuela.backend.shared.storage.UploadTestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentDocumentUploadSignatureTest {

    private static final long STUDENT_ID = 7L;

    @TempDir
    Path uploads;

    private final StudentDocumentRepository documentRepository = mock(StudentDocumentRepository.class);
    private ProgramApplicationDocumentRecorder applicationRecorder;
    private ProspectService prospectService;
    private ProgramEnrollmentApplication application;

    private ProgramApplicationDocumentRecorder recorderOver(StudentDocumentRepository repository) {
        return new ProgramApplicationDocumentRecorder(repository, new FileStorageService(uploads.toString()),
                mock(TransactionBoundFileCleanup.class), Clock.fixed(Instant.parse("2026-03-06T10:00:00Z"), ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        applicationRecorder = recorderOver(documentRepository);
        Student student = new Student();
        student.setId(STUDENT_ID);
        application = new ProgramEnrollmentApplication();
        application.setStudent(student);
        StudentFinder studentFinder = mock(StudentFinder.class);
        when(studentFinder.requireById(STUDENT_ID)).thenReturn(student);
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        prospectService = new ProspectService(studentFinder, null, documentRepository,
                new FileStorageService(uploads.toString()), mock(TransactionBoundFileCleanup.class),
                null, null, null, Clock.fixed(Instant.parse("2026-03-06T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void renamedExecutableIsRejectedByApplicationRecorder() throws IOException {
        assertThatThrownBy(() -> applicationRecorder.record(application, DocumentType.DNI_FRENTE, UploadTestFiles.renamedExecutable()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
        verify(documentRepository, never()).save(any());
    }

    @Test
    void declaredPdfWithOtherBytesIsRejectedByApplicationRecorder() throws IOException {
        assertThatThrownBy(() -> applicationRecorder.record(application, DocumentType.DNI_FRENTE, UploadTestFiles.declaredPdfWithPngBytes()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
    }

    @Test
    void validFileStillStoredByApplicationRecorder() throws IOException {
        applicationRecorder.record(application, DocumentType.DNI_FRENTE, UploadTestFiles.validPdf());

        assertThat(UploadTestFiles.countFiles(uploads)).isEqualTo(1);
    }

    @Test
    void renamedExecutableIsRejectedByProspectService() throws IOException {
        assertThatThrownBy(() -> prospectService.addDocument(STUDENT_ID, DocumentType.DNI_FRENTE, UploadTestFiles.renamedExecutable()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
        verify(documentRepository, never()).save(any());
    }

    @Test
    void declaredPdfWithOtherBytesIsRejectedByProspectService() throws IOException {
        assertThatThrownBy(() -> prospectService.addDocument(STUDENT_ID, DocumentType.DNI_FRENTE, UploadTestFiles.declaredPdfWithPngBytes()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
    }

    @Test
    void validFileStillStoredByProspectService() throws IOException {
        prospectService.addDocument(STUDENT_ID, DocumentType.DNI_FRENTE, UploadTestFiles.validPdf());

        assertThat(UploadTestFiles.countFiles(uploads)).isEqualTo(1);
    }
}
