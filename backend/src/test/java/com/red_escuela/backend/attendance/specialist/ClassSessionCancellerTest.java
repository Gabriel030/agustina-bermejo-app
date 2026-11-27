package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class ClassSessionCancellerTest {

    private final ClassSessionRepository classSessionRepository = mock(ClassSessionRepository.class);
    private final AttendanceSectionAccessChecker sectionAccess = mock(AttendanceSectionAccessChecker.class);
    private final ClassSessionUpserter classSessionUpserter = mock(ClassSessionUpserter.class);
    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ClassSessionCanceller canceller = new ClassSessionCanceller(
            classSessionRepository, sectionAccess, classSessionUpserter, attendanceRepository, eventPublisher);

    @Test
    void cancelingAProgramClassSessionRecalculatesConditionOfEnrollmentsWithAttendanceThatDate() {
        LocalDate classDate = LocalDate.of(2026, 4, 6);
        SubjectSection section = mock(SubjectSection.class);
        Instructor instructor = mock(Instructor.class);
        ClassSession session = new ClassSession();
        session.setStatus(ClassSessionStatus.SCHEDULED);
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
        when(enrollment.getId()).thenReturn(77L);
        Attendance attendance = new Attendance();
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        when(sectionAccess.findProgramSection(5L)).thenReturn(section);
        when(sectionAccess.resolveInstructor(9L)).thenReturn(instructor);
        when(classSessionUpserter.findOrCreateForProgramSection(section, classDate, instructor)).thenReturn(session);
        when(attendanceRepository.findByProgramSubjectSectionEnrollment_SubjectSection_IdAndClassDate(5L, classDate))
                .thenReturn(List.of(attendance));

        canceller.cancelProgramSectionSession(5L, 9L, classDate);

        assertThat(session.getStatus()).isEqualTo(ClassSessionStatus.CANCELED);
        verify(eventPublisher).publishEvent(new AttendanceStatusChangedEvent(77L, null));
    }
}
