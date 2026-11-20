package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.attendance.api.AttendanceProofSubmitter;
import com.red_escuela.backend.shared.storage.FileStorageService;
import com.red_escuela.backend.shared.storage.UnsupportedFileTypeException;
import com.red_escuela.backend.shared.storage.UploadTestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AcademicRecordAttendanceProofRecorderSignatureTest {

    @TempDir
    Path uploads;

    private final AttendanceProofSubmitter submitter = mock(AttendanceProofSubmitter.class);
    private AcademicRecordAttendanceProofRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new AcademicRecordAttendanceProofRecorder(submitter, new FileStorageService(uploads.toString()));
    }

    @Test
    void renamedExecutableIsRejected() throws IOException {
        assertThatThrownBy(() -> recorder.recordProof(7L, 11L, "motivo", UploadTestFiles.renamedExecutable()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
        verify(submitter, never()).submitProof(anyLong(), anyString(), anyString());
    }

    @Test
    void declaredPdfWithOtherBytesIsRejected() throws IOException {
        assertThatThrownBy(() -> recorder.recordProof(7L, 11L, "motivo", UploadTestFiles.declaredPdfWithPngBytes()))
                .isInstanceOf(UnsupportedFileTypeException.class);

        assertThat(UploadTestFiles.countFiles(uploads)).isZero();
    }

    @Test
    void validFileStillStored() throws IOException {
        try {
            recorder.recordProof(7L, 11L, "motivo", UploadTestFiles.validPdf());
        } catch (RuntimeException ignored) {
        }

        assertThat(UploadTestFiles.countFiles(uploads)).isEqualTo(1);
        verify(submitter).submitProof(anyLong(), anyString(), anyString());
    }
}
