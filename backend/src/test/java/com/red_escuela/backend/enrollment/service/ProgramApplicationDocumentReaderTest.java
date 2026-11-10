package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ProgramApplicationDocumentFile;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.exception.ApplicationDocumentNotFoundException;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.storage.FileStorageService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgramApplicationDocumentReaderTest {

    private final ProgramEnrollmentApplicationFinder finder = mock(ProgramEnrollmentApplicationFinder.class);
    private final StudentDocumentRepository documents = mock(StudentDocumentRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ProgramApplicationDocumentReader service = new ProgramApplicationDocumentReader(finder, documents, storage);

    private Student studentWithId(Long id) {
        Student student = new Student();
        student.setId(id);
        return student;
    }

    private StudentDocument documentOf(Student student, String path) {
        StudentDocument document = new StudentDocument();
        document.setId(5L);
        document.setStudent(student);
        document.setFilePath(path);
        return document;
    }

    private void applicationOf(Student student) {
        ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
        application.setId(1L);
        application.setStudent(student);
        when(finder.findOrThrow(1L)).thenReturn(application);
    }

    @Test
    void theApplicantsOwnDocumentIsReturnedWithItsMediaType() {
        Student student = studentWithId(10L);
        applicationOf(student);
        when(documents.findById(5L)).thenReturn(Optional.of(documentOf(student, "students/10/DNI_FRENTE_x.pdf")));
        when(storage.exists("students/10/DNI_FRENTE_x.pdf")).thenReturn(true);
        when(storage.readBytes("students/10/DNI_FRENTE_x.pdf")).thenReturn(new byte[]{1, 2});

        ProgramApplicationDocumentFile file = service.read(1L, 5L);

        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(file.fileName()).isEqualTo("DNI_FRENTE_x.pdf");
        assertThat(file.content()).containsExactly(1, 2);
    }

    @Test
    void aDocumentOfAnotherStudentIsNotFoundAndItsFileIsNeverRead() {
        applicationOf(studentWithId(10L));
        when(documents.findById(5L)).thenReturn(Optional.of(documentOf(studentWithId(99L), "students/99/DNI_FRENTE_x.pdf")));
        when(storage.exists("students/99/DNI_FRENTE_x.pdf")).thenReturn(true);

        assertThatThrownBy(() -> service.read(1L, 5L)).isInstanceOf(ApplicationDocumentNotFoundException.class);

        verify(storage, never()).readBytes("students/99/DNI_FRENTE_x.pdf");
    }

    @Test
    void aMissingFileOnDiskIsReportedAsNotFound() {
        Student student = studentWithId(10L);
        applicationOf(student);
        when(documents.findById(5L)).thenReturn(Optional.of(documentOf(student, "students/10/DNI_FRENTE_x.pdf")));
        when(storage.exists("students/10/DNI_FRENTE_x.pdf")).thenReturn(false);

        assertThatThrownBy(() -> service.read(1L, 5L)).isInstanceOf(ApplicationDocumentNotFoundException.class);
    }
}
