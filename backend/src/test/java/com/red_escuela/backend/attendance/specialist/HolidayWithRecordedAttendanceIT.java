package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.dto.CreateHolidayRequest;
import com.red_escuela.backend.academicstructure.dto.HolidayResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateHolidayRequest;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.HolidayCreator;
import com.red_escuela.backend.academicstructure.service.HolidayUpdater;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HolidayWithRecordedAttendanceIT extends AbstractIntegrationTest {

    private static final LocalDate MONDAY_WITH_ATTENDANCE = LocalDate.of(2026, 4, 6);
    private static final LocalDate MONDAY_WITHOUT_ATTENDANCE = LocalDate.of(2026, 4, 13);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SectionAttendanceLoader sectionAttendanceLoader;

    @Autowired
    private HolidayCreator holidayCreator;

    @Autowired
    private HolidayUpdater holidayUpdater;

    @BeforeEach
    void recordAttendanceOnOneMonday() {
        AcademicReproductionFixtures fixtures = new AcademicReproductionFixtures(entityManager);
        Instructor instructor = fixtures.instructor("77000001");
        Term term = fixtures.openTerm();
        term.setStartDate(LocalDateTime.of(2026, 3, 2, 0, 0));
        term.setEndDate(LocalDateTime.of(2026, 7, 31, 23, 59));
        SubjectSection section = fixtures.section(fixtures.subject("MateriaFeriado"), term, null);
        fixtures.assignInstructor(section, instructor);
        SubjectSectionEnrollment enrollment = fixtures.enrollInSection("770000011", section);
        Classroom classroom = new Classroom();
        classroom.setName("Aula feriado");
        classroom.setCapacity(30);
        entityManager.persist(classroom);
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setSubjectSection(section);
        slot.setClassroom(classroom);
        slot.setDayOfWeek(DayOfWeek.MONDAY);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        entityManager.persist(slot);
        entityManager.flush();
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 4, 20));
        sectionAttendanceLoader.loadProgramSectionAttendance(section.getId(), instructor.getId(),
                new CargarAsistenciaRequest(MONDAY_WITH_ATTENDANCE,
                        List.of(new AttendanceEntry(enrollment.getId(), AttendanceStatus.PRESENT)), null, null));
        entityManager.flush();
    }

    @Test
    void creatingAHolidayOnADateWithRecordedAttendanceSucceedsAndListsTheAffectedDate() {
        HolidayResponse response = holidayCreator.create(
                new CreateHolidayRequest(MONDAY_WITH_ATTENDANCE, "Feriado retroactivo", HolidayType.NATIONAL, null));

        assertThat(response.attendanceAffectedDates()).containsExactly(MONDAY_WITH_ATTENDANCE);
    }

    @Test
    void creatingAHolidayOnADateWithoutAttendanceListsNoAffectedDates() {
        HolidayResponse response = holidayCreator.create(
                new CreateHolidayRequest(MONDAY_WITHOUT_ATTENDANCE, "Feriado futuro", HolidayType.NATIONAL, null));

        assertThat(response.attendanceAffectedDates()).isEmpty();
    }

    @Test
    void movingAHolidayOntoADateWithRecordedAttendanceListsTheAffectedDate() {
        HolidayResponse created = holidayCreator.create(
                new CreateHolidayRequest(MONDAY_WITHOUT_ATTENDANCE, "Feriado movible", HolidayType.NATIONAL, null));

        HolidayResponse moved = holidayUpdater.update(created.id(),
                new UpdateHolidayRequest(MONDAY_WITH_ATTENDANCE, "Feriado movible", HolidayType.NATIONAL, null));

        assertThat(moved.attendanceAffectedDates()).containsExactly(MONDAY_WITH_ATTENDANCE);
    }
}
