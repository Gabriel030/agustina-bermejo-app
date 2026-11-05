package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.exception.ApplicationDocumentsLockedException;
import com.red_escuela.backend.enrollment.service.ProgramApplicationDocumentRecorder;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddProgramEnrollmentApplicationDocumentServiceTest {

    private final ProgramEnrollmentApplicationFinder finder = mock(ProgramEnrollmentApplicationFinder.class);
    private final ProgramApplicationDocumentRecorder recorder = mock(ProgramApplicationDocumentRecorder.class);
    private final AddProgramEnrollmentApplicationDocumentService service =
            new AddProgramEnrollmentApplicationDocumentService(finder, recorder);
    private final MockMultipartFile file = new MockMultipartFile("file", "dni.pdf", "application/pdf", new byte[]{1});

    private ProgramEnrollmentApplication applicationIn(ProgramEnrollmentApplicationStatus status) {
        ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
        application.setId(7L);
        application.setStatus(status);
        when(finder.findOrThrow(7L)).thenReturn(application);
        return application;
    }

    @Test
    void pendingApplicationAcceptsTheDocument() {
        ProgramEnrollmentApplication application = applicationIn(ProgramEnrollmentApplicationStatus.PENDING);
        StudentDocument stored = new StudentDocument();
        when(recorder.record(application, DocumentType.DNI_FRENTE, file)).thenReturn(stored);

        StudentDocument result = service.add(new AddProgramEnrollmentApplicationDocumentCommand(7L, DocumentType.DNI_FRENTE, file));

        assertThat(result).isSameAs(stored);
    }

    @ParameterizedTest
    @EnumSource(value = ProgramEnrollmentApplicationStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void nonPendingApplicationRejectsTheDocumentAndKeepsTheExistingOne(ProgramEnrollmentApplicationStatus status) {
        ProgramEnrollmentApplication application = applicationIn(status);

        assertThatThrownBy(() -> service.add(new AddProgramEnrollmentApplicationDocumentCommand(7L, DocumentType.DNI_FRENTE, file)))
                .isInstanceOf(ApplicationDocumentsLockedException.class)
                .hasMessageContaining(status.name());

        verify(recorder, never()).record(application, DocumentType.DNI_FRENTE, file);
    }
}
