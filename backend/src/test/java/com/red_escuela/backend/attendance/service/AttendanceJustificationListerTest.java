package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.domain.AttendanceJustificationStatus;
import com.red_escuela.backend.attendance.dto.AttendanceJustificationPage;
import com.red_escuela.backend.attendance.dto.AttendanceJustificationResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceJustificationListerTest {

    private static final LocalDate CLASS_DAY = LocalDate.of(2026, 4, 6);
    private static final LocalDateTime DECIDED_AT = LocalDateTime.of(2026, 4, 8, 10, 0);

    @Mock
    private AttendanceRepository attendanceRepository;

    private AttendanceJustificationLister lister() {
        return new AttendanceJustificationLister(attendanceRepository);
    }

    private static Student student() {
        Person person = new Person();
        person.setName("Ana");
        person.setLastname("Perez");
        Student student = new Student();
        student.setPerson(person);
        return student;
    }

    private static Attendance programAttendance(AttendanceStatus status) {
        Subject subject = new Subject();
        subject.setName("Matematica");
        SubjectSection section = new SubjectSection();
        section.setName("Comision A");
        section.setSubject(subject);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student());
        enrollment.setSubjectSection(section);
        Attendance attendance = new Attendance();
        attendance.setId(1L);
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(CLASS_DAY);
        attendance.setStatus(status);
        attendance.setJustificationReason("Fiebre");
        attendance.setJustificationAttachmentUrl("http://files/cert.pdf");
        return attendance;
    }

    private static Attendance courseAttendance(AttendanceStatus status) {
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setName("Modulo 1");
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Turno noche");
        section.setCourseSubject(courseSubject);
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student());
        enrollment.setCourseSubjectSection(section);
        Attendance attendance = new Attendance();
        attendance.setId(2L);
        attendance.setCourseSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(CLASS_DAY);
        attendance.setStatus(status);
        attendance.setJustificationReason("Viaje");
        return attendance;
    }

    @Test
    void pendingListReadsJustificationPendingAttendanceWithoutDecisionDate() {
        when(attendanceRepository.findByStatus(eq(AttendanceStatus.JUSTIFICATION_PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(programAttendance(AttendanceStatus.JUSTIFICATION_PENDING))));

        AttendanceJustificationPage page = lister().listByStatus(AttendanceJustificationStatus.PENDING, 0, 10);

        assertThat(page.totalElements()).isEqualTo(1);
        AttendanceJustificationResponse item = page.items().get(0);
        assertThat(item.studentName()).isEqualTo("Ana Perez");
        assertThat(item.subjectName()).isEqualTo("Matematica");
        assertThat(item.sectionName()).isEqualTo("Comision A");
        assertThat(item.status()).isEqualTo("PENDING");
        assertThat(item.reason()).isEqualTo("Fiebre");
        assertThat(item.attachmentUrl()).isEqualTo("http://files/cert.pdf");
        assertThat(item.decidedAt()).isNull();
    }

    @Test
    void approvedListReadsJustifiedAttendanceWithTheJustificationDate() {
        Attendance attendance = programAttendance(AttendanceStatus.JUSTIFIED);
        attendance.setJustifiedAt(DECIDED_AT);
        attendance.setJustificationRejectedAt(DECIDED_AT.plusDays(5));
        when(attendanceRepository.findByStatusAndJustificationReasonIsNotNull(eq(AttendanceStatus.JUSTIFIED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(attendance)));

        AttendanceJustificationResponse item = lister().listByStatus(AttendanceJustificationStatus.APPROVED, 0, 10).items().get(0);

        assertThat(item.status()).isEqualTo("APPROVED");
        assertThat(item.decidedAt()).isEqualTo(DECIDED_AT);
    }

    @Test
    void rejectedListReadsAbsentAttendanceWithTheRejectionReasonAndDate() {
        Attendance attendance = courseAttendance(AttendanceStatus.ABSENT);
        attendance.setJustificationRejectionReason("Comprobante ilegible");
        attendance.setJustificationRejectedAt(DECIDED_AT);
        attendance.setJustifiedAt(DECIDED_AT.minusDays(3));
        when(attendanceRepository.findByStatusAndJustificationRejectionReasonIsNotNull(eq(AttendanceStatus.ABSENT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(attendance)));

        AttendanceJustificationResponse item = lister().listByStatus(AttendanceJustificationStatus.REJECTED, 0, 10).items().get(0);

        assertThat(item.status()).isEqualTo("REJECTED");
        assertThat(item.rejectionReason()).isEqualTo("Comprobante ilegible");
        assertThat(item.decidedAt()).isEqualTo(DECIDED_AT);
        assertThat(item.subjectName()).isEqualTo("Modulo 1");
        assertThat(item.sectionName()).isEqualTo("Turno noche");
    }

    @Test
    void courseAttendanceIsResolvedThroughTheCourseEnrollment() {
        when(attendanceRepository.findByStatus(eq(AttendanceStatus.JUSTIFICATION_PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(courseAttendance(AttendanceStatus.JUSTIFICATION_PENDING))));

        AttendanceJustificationResponse item = lister().listByStatus(AttendanceJustificationStatus.PENDING, 0, 10).items().get(0);

        assertThat(item.studentName()).isEqualTo("Ana Perez");
        assertThat(item.subjectName()).isEqualTo("Modulo 1");
    }

    @Test
    void pageSizeIsCappedAtTheMaximumAndNegativePageBecomesZero() {
        when(attendanceRepository.findByStatus(eq(AttendanceStatus.JUSTIFICATION_PENDING), any(Pageable.class)))
                .thenReturn(Page.empty());

        lister().listByStatus(AttendanceJustificationStatus.PENDING, -3, 5000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(attendanceRepository).findByStatus(eq(AttendanceStatus.JUSTIFICATION_PENDING), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(AttendanceJustificationLister.MAX_PAGE_SIZE);
    }

    @Test
    void pageSizeBelowOneBecomesOneAndResultsAreSortedByDateThenId() {
        when(attendanceRepository.findByStatus(eq(AttendanceStatus.JUSTIFICATION_PENDING), any(Pageable.class)))
                .thenReturn(Page.empty());

        lister().listByStatus(AttendanceJustificationStatus.PENDING, 2, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(attendanceRepository).findByStatus(eq(AttendanceStatus.JUSTIFICATION_PENDING), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
        assertThat(captor.getValue().getSort().stream().map(order -> order.getProperty()).toList())
                .containsExactly("classDate", "id");
    }
}
