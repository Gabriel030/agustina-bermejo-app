package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.ExportedAttendanceFile;
import com.red_escuela.backend.attendance.service.AttendanceExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportCourseSectionAttendanceServiceTest {

    @Mock
    private AttendanceExportService exportService;

    @Test
    void delegatesTheCommandFieldsToTheExportService() {
        LocalDate day = LocalDate.of(2026, 5, 4);
        ExportedAttendanceFile expected = new ExportedAttendanceFile(new byte[] {1}, "history.pdf", "application/pdf");
        when(exportService.exportCourseSection(5L, 9L, "history", "pdf", day)).thenReturn(expected);

        ExportedAttendanceFile result = new ExportCourseSectionAttendanceService(exportService)
                .export(new ExportCourseSectionAttendanceCommand(5L, 9L, "history", "pdf", day));

        assertThat(result).isSameAs(expected);
    }
}
