package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CanceledClassSessionAbsencesReproductionIT extends ReproductionFixture {

    @Autowired
    private AttendanceStandingReader attendanceStandingReader;

    @Test
    @Disabled("E34-B-P8")
    void absencesRecordedOnACanceledClassDateDoNotCountTowardsAbsenceStanding() {
        Program program = program(4);
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        SubjectSection section = section(subject, term);
        ProgramEnrollment programEnrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment enrollment = sectionEnrollment(programEnrollment, section, EnrollmentStatus.IN_PROGRESS);
        Instructor instructor = new Instructor();
        instructor.setPerson(person());
        entityManager.persist(instructor);
        LocalDate canceledDate = LocalDate.of(2026, 4, 6);

        ClassSession session = new ClassSession();
        session.setSubjectSection(section);
        session.setClassDate(canceledDate);
        session.setStatus(ClassSessionStatus.CANCELED);
        session.setCreatedBy(instructor);
        session.setCreatedAt(LocalDateTime.of(2026, 4, 1, 9, 0));
        entityManager.persist(session);

        Attendance absence = new Attendance();
        absence.setProgramSubjectSectionEnrollment(enrollment);
        absence.setClassDate(canceledDate);
        absence.setStatus(AttendanceStatus.ABSENT);
        absence.setRecordedBy(instructor);
        entityManager.persist(absence);
        flushAndClear();

        AttendanceStanding standing = attendanceStandingReader.readProgramEnrollmentStanding(enrollment.getId());

        assertThat(standing.countableAbsences()).isZero();
    }
}
