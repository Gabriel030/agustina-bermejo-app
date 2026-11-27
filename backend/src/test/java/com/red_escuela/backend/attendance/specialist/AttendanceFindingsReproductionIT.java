package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceFindingsReproductionIT extends AbstractIntegrationTest {

    private static final LocalDate VALID_MONDAY = LocalDate.of(2026, 4, 6);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SectionAttendanceLoader sectionAttendanceLoader;

    private AcademicReproductionFixtures fixtures;

    @BeforeEach
    void createFixtures() {
        fixtures = new AcademicReproductionFixtures(entityManager);
    }

    private record Setup(Instructor instructor, SubjectSection section, SubjectSectionEnrollment enrollment) {
    }

    private Setup setup(String dni) {
        Instructor instructor = fixtures.instructor(dni);
        Term term = fixtures.openTerm();
        term.setStartDate(LocalDateTime.of(2026, 3, 2, 0, 0));
        term.setEndDate(LocalDateTime.of(2026, 7, 31, 23, 59));
        SubjectSection section = fixtures.section(fixtures.subject("Materia" + dni), term, null);
        fixtures.assignInstructor(section, instructor);
        SubjectSectionEnrollment enrollment = fixtures.enrollInSection(dni + "1", section);
        entityManager.flush();
        return new Setup(instructor, section, enrollment);
    }

    private void addSlot(SubjectSection section, DayOfWeek day) {
        Classroom classroom = new Classroom();
        classroom.setName("Aula " + section.getId() + day);
        classroom.setCapacity(30);
        entityManager.persist(classroom);
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setSubjectSection(section);
        slot.setClassroom(classroom);
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        entityManager.persist(slot);
        entityManager.flush();
    }

    private void load(Setup setup, LocalDate classDate) {
        sectionAttendanceLoader.loadProgramSectionAttendance(setup.section().getId(), setup.instructor().getId(),
                new CargarAsistenciaRequest(classDate,
                        List.of(new AttendanceEntry(setup.enrollment().getId(), AttendanceStatus.PRESENT)), null, null));
    }

    @Test
    void h3AttendanceCannotBeLoadedWhileTheClosureIsClosed() {
        Setup setup = setup("76000001");
        fixtures.closure(SectionKind.PROGRAM, setup.section().getId(), ClosureStatus.CLOSED,
                setup.instructor().getPerson().getId());
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 4, 20));

        assertThatThrownBy(() -> load(setup, VALID_MONDAY)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void h5AttendanceCannotBeLoadedOnASaturday() {
        Setup setup = setup("76000003");
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 4, 20));

        assertThatThrownBy(() -> load(setup, LocalDate.of(2026, 4, 4))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void h5AttendanceCannotBeLoadedOnASunday() {
        Setup setup = setup("76000005");
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 4, 20));

        assertThatThrownBy(() -> load(setup, LocalDate.of(2026, 4, 5))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void h5AttendanceCannotBeLoadedBeforeTheTermStarts() {
        Setup setup = setup("76000007");
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> load(setup, LocalDate.of(2026, 2, 23))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void h5AttendanceCannotBeLoadedAfterTheTermEnds() {
        Setup setup = setup("76000009");
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> load(setup, LocalDate.of(2026, 8, 3))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void h5AttendanceCannotBeLoadedForAFutureDate() {
        Setup setup = setup("76000011");
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 5, 4));

        assertThatThrownBy(() -> load(setup, LocalDate.of(2026, 5, 11))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void h5AttendanceCannotBeLoadedOnADayWithoutAScheduleSlot() {
        Setup setup = setup("76000013");
        addSlot(setup.section(), DayOfWeek.MONDAY);
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 4, 20));

        assertThatThrownBy(() -> load(setup, LocalDate.of(2026, 4, 7))).isInstanceOf(RuntimeException.class);
    }
}
