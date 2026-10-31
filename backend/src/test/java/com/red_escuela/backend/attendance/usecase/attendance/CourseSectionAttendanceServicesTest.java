package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.exception.ClassOnHolidayException;
import com.red_escuela.backend.attendance.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.attendance.service.AttendanceFinder;
import com.red_escuela.backend.attendance.service.AttendanceHistoryBuilder;
import com.red_escuela.backend.attendance.service.AttendanceRiskCalculator;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.ClassSessionUpserter;
import com.red_escuela.backend.attendance.service.SectionAttendanceRecorder;
import com.red_escuela.backend.attendance.service.SectionRosterBuilder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CourseSectionAttendanceServicesTest {

    private static final Long SECTION_ID = 7L;
    private static final Long INSTRUCTOR_ID = 3L;
    private static final Long OTHER_INSTRUCTOR_ID = 99L;

    private final AttendanceSectionAccess sectionAccess = mock(AttendanceSectionAccess.class);
    private final SectionAttendanceRecorder attendanceRecorder = mock(SectionAttendanceRecorder.class);
    private final ClassSessionUpserter classSessionUpserter = mock(ClassSessionUpserter.class);
    private final ClassSessionRepository classSessionRepository = mock(ClassSessionRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SectionRosterBuilder rosterBuilder = mock(SectionRosterBuilder.class);
    private final AttendanceHistoryBuilder historyBuilder = mock(AttendanceHistoryBuilder.class);
    private final CourseSubjectSection section = mock(CourseSubjectSection.class);
    private final Instructor instructor = mock(Instructor.class);
    private final AttendanceFinder attendanceFinder = mock(AttendanceFinder.class);
    private final AttendanceRiskCalculator riskCalculator = mock(AttendanceRiskCalculator.class);
    private static final LocalDate CLASS_DAY = LocalDate.of(2025, 9, 15);

    private final LoadCourseSectionAttendanceService loadService = new LoadCourseSectionAttendanceService(
            attendanceRecorder, sectionAccess, classSessionUpserter, eventPublisher);
    private final CancelCourseSectionClassSessionService cancelService = new CancelCourseSectionClassSessionService(
            classSessionRepository, sectionAccess, classSessionUpserter);
    private final GetCourseSectionAbsenceReportService absenceReportService =
            new GetCourseSectionAbsenceReportService(sectionAccess, rosterBuilder);
    private final GetCourseSectionAttendanceHistoryService historyService =
            new GetCourseSectionAttendanceHistoryService(sectionAccess, historyBuilder);
    private final GetCourseSectionRosterService rosterService =
            new GetCourseSectionRosterService(sectionAccess, rosterBuilder);
    private final GetCourseSectionClassSessionService classSessionService =
            new GetCourseSectionClassSessionService(classSessionRepository, sectionAccess);

    private LocalDate today() {
        return CLASS_DAY;
    }

    private Attendance attendanceOfEnrollment(long enrollmentId) {
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class);
        when(enrollment.getId()).thenReturn(enrollmentId);
        Attendance attendance = mock(Attendance.class);
        when(attendance.getCourseSubjectSectionEnrollment()).thenReturn(enrollment);
        return attendance;
    }

    private void givenOwnSection() {
        when(sectionAccess.findCourseSection(SECTION_ID)).thenReturn(section);
        when(sectionAccess.resolveInstructor(INSTRUCTOR_ID)).thenReturn(instructor);
    }

    private void givenInstructorDoesNotTeach(Long instructorId) {
        doThrow(new NotOwnSectionException(instructorId, SECTION_ID))
                .when(sectionAccess).requireOwnCourseSection(SECTION_ID, instructorId);
        doThrow(new NotOwnSectionException(instructorId, SECTION_ID))
                .when(sectionAccess).requireOwnCourseSectionIfInstructorGiven(SECTION_ID, instructorId);
    }

    private LoadCourseSectionAttendanceCommand loadCommand(LocalDate date, AttendanceEntry... entries) {
        return new LoadCourseSectionAttendanceCommand(SECTION_ID, INSTRUCTOR_ID,
                new CargarAsistenciaRequest(date, List.of(entries), "Tema 4", "Sin novedades"));
    }

    @Test
    void loadRecordsEveryEntryAndMarksTheClassAsHeldWithTopicAndObservations() {
        givenOwnSection();
        Attendance first = attendanceOfEnrollment(10L);
        Attendance second = attendanceOfEnrollment(11L);
        when(attendanceRecorder.recordCourseAttendance(10L, today(), AttendanceStatus.PRESENT, instructor)).thenReturn(first);
        when(attendanceRecorder.recordCourseAttendance(11L, today(), AttendanceStatus.ABSENT, instructor)).thenReturn(second);

        List<Attendance> result = loadService.load(loadCommand(today(),
                new AttendanceEntry(10L, AttendanceStatus.PRESENT), new AttendanceEntry(11L, AttendanceStatus.ABSENT)));

        assertThat(result).containsExactly(first, second);
        verify(classSessionUpserter).upsertHeldForCourseSection(section, today(), instructor, "Tema 4", "Sin novedades");
    }

    @Test
    void loadPublishesOneStatusChangedEventPerRecordedEnrollmentAfterTheSessionIsHeld() {
        givenOwnSection();
        Attendance first = attendanceOfEnrollment(10L);
        Attendance second = attendanceOfEnrollment(11L);
        when(attendanceRecorder.recordCourseAttendance(eq(10L), any(), any(), any())).thenReturn(first);
        when(attendanceRecorder.recordCourseAttendance(eq(11L), any(), any(), any())).thenReturn(second);

        loadService.load(loadCommand(today(),
                new AttendanceEntry(10L, AttendanceStatus.PRESENT), new AttendanceEntry(11L, AttendanceStatus.JUSTIFIED)));

        InOrder order = inOrder(classSessionUpserter, eventPublisher);
        order.verify(classSessionUpserter).upsertHeldForCourseSection(any(), any(), any(), any(), any());
        order.verify(eventPublisher).publishEvent(new AttendanceStatusChangedEvent(null, 10L));
        order.verify(eventPublisher).publishEvent(new AttendanceStatusChangedEvent(null, 11L));
    }

    @Test
    void loadKeepsOnlyTheLastStatusWhenTheSameEnrollmentAppearsTwice() {
        givenOwnSection();
        Attendance saved = attendanceOfEnrollment(10L);
        when(attendanceRecorder.recordCourseAttendance(10L, today(), AttendanceStatus.LATE, instructor)).thenReturn(saved);

        List<Attendance> result = loadService.load(loadCommand(today(),
                new AttendanceEntry(10L, AttendanceStatus.ABSENT), new AttendanceEntry(10L, AttendanceStatus.LATE)));

        assertThat(result).containsExactly(saved);
        verify(attendanceRecorder).recordCourseAttendance(10L, today(), AttendanceStatus.LATE, instructor);
        verify(attendanceRecorder, never()).recordCourseAttendance(10L, today(), AttendanceStatus.ABSENT, instructor);
    }

    @Test
    void loadRejectsAnInstructorWhoDoesNotTeachTheSectionBeforeTouchingAnything() {
        when(sectionAccess.findCourseSection(SECTION_ID)).thenReturn(section);
        givenInstructorDoesNotTeach(INSTRUCTOR_ID);

        assertThatThrownBy(() -> loadService.load(loadCommand(today(), new AttendanceEntry(10L, AttendanceStatus.PRESENT))))
                .isInstanceOf(NotOwnSectionException.class);

        verifyNoInteractions(attendanceRecorder, classSessionUpserter, eventPublisher);
    }

    @Test
    void loadFailsWhenTheSectionDoesNotExist() {
        when(sectionAccess.findCourseSection(SECTION_ID)).thenThrow(new SectionNotFoundException(SECTION_ID));

        assertThatThrownBy(() -> loadService.load(loadCommand(today(), new AttendanceEntry(10L, AttendanceStatus.PRESENT))))
                .isInstanceOf(SectionNotFoundException.class);

        verifyNoInteractions(attendanceRecorder, classSessionUpserter, eventPublisher);
    }

    @Test
    void loadRefusesToTakeAttendanceOnAHolidayWithoutRecordingOrPublishingAnything() {
        givenOwnSection();
        when(sectionAccess.isHoliday(today())).thenReturn(true);

        assertThatThrownBy(() -> loadService.load(loadCommand(today(), new AttendanceEntry(10L, AttendanceStatus.PRESENT))))
                .isInstanceOf(ClassOnHolidayException.class)
                .hasMessageContaining(today().toString());

        verifyNoInteractions(attendanceRecorder, classSessionUpserter, eventPublisher);
    }

    @Test
    void loadOnlyChecksTheHolidayOfTheRequestedClassDate() {
        givenOwnSection();
        LocalDate holiday = today();
        LocalDate workingDay = today().plusDays(1);
        when(sectionAccess.isHoliday(holiday)).thenReturn(true);
        Attendance saved = attendanceOfEnrollment(10L);
        when(attendanceRecorder.recordCourseAttendance(eq(10L), eq(workingDay), any(), any())).thenReturn(saved);

        assertThat(loadService.load(loadCommand(workingDay, new AttendanceEntry(10L, AttendanceStatus.PRESENT)))).hasSize(1);
        verify(sectionAccess).isHoliday(workingDay);
    }

    @Test
    void loadPropagatesTheRecorderFailureWithoutPublishingEvents() {
        givenOwnSection();
        when(attendanceRecorder.recordCourseAttendance(anyLong(), any(), any(), any()))
                .thenThrow(new IllegalStateException("status not recordable"));

        assertThatThrownBy(() -> loadService.load(loadCommand(today(), new AttendanceEntry(10L, AttendanceStatus.PRESENT))))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(eventPublisher);
        verify(classSessionUpserter, never()).upsertHeldForCourseSection(any(), any(), any(), any(), any());
    }

    @Test
    void cancelMarksTheScheduledSessionAsCanceledAndSavesIt() {
        givenOwnSection();
        ClassSession session = new ClassSession();
        session.setStatus(ClassSessionStatus.SCHEDULED);
        when(classSessionUpserter.findOrCreateForCourseSection(section, today(), instructor)).thenReturn(session);

        cancelService.cancel(new CancelCourseSectionClassSessionCommand(SECTION_ID, INSTRUCTOR_ID, today()));

        assertThat(session.getStatus()).isEqualTo(ClassSessionStatus.CANCELED);
        verify(classSessionRepository).save(session);
    }

    @Test
    void cancelAlsoCancelsASessionThatWasAlreadyHeld() {
        givenOwnSection();
        ClassSession session = new ClassSession();
        session.setStatus(ClassSessionStatus.HELD);
        when(classSessionUpserter.findOrCreateForCourseSection(section, today(), instructor)).thenReturn(session);

        cancelService.cancel(new CancelCourseSectionClassSessionCommand(SECTION_ID, INSTRUCTOR_ID, today()));

        assertThat(session.getStatus()).isEqualTo(ClassSessionStatus.CANCELED);
        verify(classSessionRepository).save(session);
    }

    @Test
    void cancelRejectsAnAlreadyCanceledSessionWithoutSavingAgain() {
        givenOwnSection();
        ClassSession session = new ClassSession();
        session.setStatus(ClassSessionStatus.CANCELED);
        when(classSessionUpserter.findOrCreateForCourseSection(section, today(), instructor)).thenReturn(session);

        assertThatThrownBy(() -> cancelService.cancel(new CancelCourseSectionClassSessionCommand(SECTION_ID, INSTRUCTOR_ID, today())))
                .isInstanceOf(ClassSessionAlreadyCanceledException.class);

        verify(classSessionRepository, never()).save(any());
    }

    @Test
    void cancelRejectsAnInstructorOfAnotherSectionBeforeCreatingTheSession() {
        when(sectionAccess.findCourseSection(SECTION_ID)).thenReturn(section);
        givenInstructorDoesNotTeach(INSTRUCTOR_ID);

        assertThatThrownBy(() -> cancelService.cancel(new CancelCourseSectionClassSessionCommand(SECTION_ID, INSTRUCTOR_ID, today())))
                .isInstanceOf(NotOwnSectionException.class);

        verifyNoInteractions(classSessionUpserter, classSessionRepository);
    }

    @Test
    void cancelFailsWhenTheSectionDoesNotExist() {
        when(sectionAccess.findCourseSection(SECTION_ID)).thenThrow(new SectionNotFoundException(SECTION_ID));

        assertThatThrownBy(() -> cancelService.cancel(new CancelCourseSectionClassSessionCommand(SECTION_ID, INSTRUCTOR_ID, today())))
                .isInstanceOf(SectionNotFoundException.class);

        verifyNoInteractions(classSessionUpserter, classSessionRepository);
    }

    @Test
    void absenceReportFiltersByMinimumAbsencesForADirectorWithoutOwnershipCheck() {
        List<RosterEntryResponse> roster = List.of(rosterEntry(1L, 5));
        List<RosterEntryResponse> sorted = List.of(rosterEntry(1L, 5));
        when(rosterBuilder.buildForCourseSection(SECTION_ID)).thenReturn(roster);
        when(rosterBuilder.sortByAbsences(roster, 3)).thenReturn(sorted);

        List<RosterEntryResponse> result = absenceReportService.get(new GetCourseSectionAbsenceReportCommand(SECTION_ID, null, 3));

        assertThat(result).isSameAs(sorted);
        verify(sectionAccess).requireCourseSectionExists(SECTION_ID);
        verify(sectionAccess).requireOwnCourseSectionIfInstructorGiven(SECTION_ID, null);
    }

    @Test
    void absenceReportRejectsAnInstructorOfAnotherSection() {
        givenInstructorDoesNotTeach(OTHER_INSTRUCTOR_ID);

        assertThatThrownBy(() -> absenceReportService.get(new GetCourseSectionAbsenceReportCommand(SECTION_ID, OTHER_INSTRUCTOR_ID, null)))
                .isInstanceOf(NotOwnSectionException.class);

        verifyNoInteractions(rosterBuilder);
    }

    @Test
    void absenceReportFailsWhenTheSectionDoesNotExist() {
        doThrow(new SectionNotFoundException(SECTION_ID)).when(sectionAccess).requireCourseSectionExists(SECTION_ID);

        assertThatThrownBy(() -> absenceReportService.get(new GetCourseSectionAbsenceReportCommand(SECTION_ID, null, null)))
                .isInstanceOf(SectionNotFoundException.class);

        verifyNoInteractions(rosterBuilder);
    }

    @Test
    void historyReturnsTheBuilderResultForTheOwnInstructor() {
        AttendanceHistoryResponse history = new AttendanceHistoryResponse(List.of(today()), List.of());
        when(historyBuilder.buildForCourseSection(SECTION_ID)).thenReturn(history);

        AttendanceHistoryResponse result = historyService.get(new GetCourseSectionAttendanceHistoryCommand(SECTION_ID, INSTRUCTOR_ID));

        assertThat(result).isSameAs(history);
        verify(sectionAccess).requireOwnCourseSectionIfInstructorGiven(SECTION_ID, INSTRUCTOR_ID);
    }

    @Test
    void historyRejectsAnInstructorOfAnotherSection() {
        givenInstructorDoesNotTeach(OTHER_INSTRUCTOR_ID);

        assertThatThrownBy(() -> historyService.get(new GetCourseSectionAttendanceHistoryCommand(SECTION_ID, OTHER_INSTRUCTOR_ID)))
                .isInstanceOf(NotOwnSectionException.class);

        verifyNoInteractions(historyBuilder);
    }

    @Test
    void historyFailsWhenTheSectionDoesNotExist() {
        doThrow(new SectionNotFoundException(SECTION_ID)).when(sectionAccess).requireCourseSectionExists(SECTION_ID);

        assertThatThrownBy(() -> historyService.get(new GetCourseSectionAttendanceHistoryCommand(SECTION_ID, null)))
                .isInstanceOf(SectionNotFoundException.class);

        verifyNoInteractions(historyBuilder);
    }

    @Test
    void rosterReturnsTheEntriesOfTheOwnSection() {
        List<RosterEntryResponse> roster = List.of(rosterEntry(1L, 0), rosterEntry(2L, 2));
        when(rosterBuilder.buildForCourseSection(SECTION_ID)).thenReturn(roster);

        assertThat(rosterService.get(new GetCourseSectionRosterCommand(SECTION_ID, INSTRUCTOR_ID))).isSameAs(roster);
        verify(sectionAccess).requireOwnCourseSection(SECTION_ID, INSTRUCTOR_ID);
    }

    @Test
    void rosterRejectsAnInstructorOfAnotherSection() {
        givenInstructorDoesNotTeach(OTHER_INSTRUCTOR_ID);

        assertThatThrownBy(() -> rosterService.get(new GetCourseSectionRosterCommand(SECTION_ID, OTHER_INSTRUCTOR_ID)))
                .isInstanceOf(NotOwnSectionException.class);

        verifyNoInteractions(rosterBuilder);
    }

    @Test
    void rosterFailsWhenTheSectionDoesNotExist() {
        doThrow(new SectionNotFoundException(SECTION_ID)).when(sectionAccess).requireCourseSectionExists(SECTION_ID);

        assertThatThrownBy(() -> rosterService.get(new GetCourseSectionRosterCommand(SECTION_ID, INSTRUCTOR_ID)))
                .isInstanceOf(SectionNotFoundException.class);

        verifyNoInteractions(rosterBuilder);
    }

    @Test
    void classSessionLookupReturnsTheSessionOfThatDate() {
        ClassSession session = new ClassSession();
        when(classSessionRepository.findByCourseSubjectSectionIdAndClassDate(SECTION_ID, today())).thenReturn(Optional.of(session));

        assertThat(classSessionService.get(new GetCourseSectionClassSessionCommand(SECTION_ID, INSTRUCTOR_ID, today())))
                .isSameAs(session);
    }

    @Test
    void classSessionLookupReturnsNullWhenNoSessionExistsForThatDate() {
        when(classSessionRepository.findByCourseSubjectSectionIdAndClassDate(SECTION_ID, today())).thenReturn(Optional.empty());

        assertThat(classSessionService.get(new GetCourseSectionClassSessionCommand(SECTION_ID, INSTRUCTOR_ID, today()))).isNull();
    }

    @Test
    void classSessionLookupRejectsAnInstructorOfAnotherSectionWithoutQueryingTheRepository() {
        givenInstructorDoesNotTeach(OTHER_INSTRUCTOR_ID);

        assertThatThrownBy(() -> classSessionService.get(new GetCourseSectionClassSessionCommand(SECTION_ID, OTHER_INSTRUCTOR_ID, today())))
                .isInstanceOf(NotOwnSectionException.class);

        verifyNoInteractions(classSessionRepository);
    }

    @Test
    void getAttendanceReturnsTheFoundAttendance() {
        Attendance attendance = mock(Attendance.class);
        when(attendanceFinder.findOrThrow(5L)).thenReturn(attendance);

        assertThat(new GetAttendanceService(attendanceFinder).get(5L)).isSameAs(attendance);
    }

    @Test
    void getAttendanceFailsWhenItDoesNotExist() {
        when(attendanceFinder.findOrThrow(5L)).thenThrow(new AttendanceNotFoundException(5L));

        assertThatThrownBy(() -> new GetAttendanceService(attendanceFinder).get(5L))
                .isInstanceOf(AttendanceNotFoundException.class);
    }

    @Test
    void courseEnrollmentRiskDelegatesToTheCourseCalculationNotTheProgramOne() {
        AttendanceRiskResponse risk = new AttendanceRiskResponse(true, false, Percentage.ofPoints(java.math.BigDecimal.TEN), Percentage.ofPoints(java.math.BigDecimal.valueOf(25)));
        when(riskCalculator.calculateForCourseEnrollment(12L)).thenReturn(risk);

        assertThat(new CalculateCourseEnrollmentAttendanceRiskService(riskCalculator).calculate(12L)).isSameAs(risk);
        verify(riskCalculator, never()).calculateForProgramEnrollment(anyLong());
    }

    private RosterEntryResponse rosterEntry(Long enrollmentId, long absences) {
        return new RosterEntryResponse(enrollmentId, enrollmentId + 100, "Alumno " + enrollmentId, "DNI" + enrollmentId,
                absences, Percentage.ofPoints(java.math.BigDecimal.valueOf(80)), absences >= 4);
    }
}
