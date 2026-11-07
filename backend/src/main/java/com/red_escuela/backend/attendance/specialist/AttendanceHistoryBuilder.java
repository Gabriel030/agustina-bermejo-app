package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.JustificationDeadlinePolicy;
import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttendanceHistoryBuilder {

    private final AttendanceRepository attendanceRepository;
    private final ClassSessionRepository classSessionRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final JustificationDeadlinePolicy deadlinePolicy;

    public AttendanceHistoryBuilder(AttendanceRepository attendanceRepository,
                                     ClassSessionRepository classSessionRepository,
                                     SectionEnrollmentFinder sectionEnrollmentFinder,
                                     JustificationDeadlinePolicy deadlinePolicy) {
        this.attendanceRepository = attendanceRepository;
        this.classSessionRepository = classSessionRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.deadlinePolicy = deadlinePolicy;
    }

    public AttendanceHistoryResponse buildForProgramSection(Long sectionId) {
        Set<LocalDate> fechasCanceladas = classSessionRepository.findBySubjectSectionId(sectionId).stream()
                .filter(cs -> cs.getStatus() == ClassSessionStatus.CANCELED)
                .map(ClassSession::getClassDate)
                .collect(Collectors.toSet());
        List<Attendance> asistencias = attendanceRepository.findByProgramSubjectSectionEnrollment_SubjectSection_Id(sectionId)
                .stream().filter(a -> !fechasCanceladas.contains(a.getClassDate())).toList();
        Map<Long, List<Attendance>> porInscripcion = asistencias.stream()
                .collect(Collectors.groupingBy(a -> a.getProgramSubjectSectionEnrollment().getId()));

        Map<LocalDate, LocalDate> deadlines = deadlinePolicy.deadlinesOf(
                asistencias.stream().map(Attendance::getClassDate).toList());
        List<SubjectSectionEnrollment> inscriptos =
                sectionEnrollmentFinder.findActiveInProgramSection(sectionId);
        List<AttendanceHistoryResponse.StudentHistoryRow> filas = inscriptos.stream()
                .map(enrollment -> buildRow(
                        enrollment.getStudent().getId(), enrollment.getStudent().getPerson(),
                        porInscripcion.getOrDefault(enrollment.getId(), List.of()), deadlines))
                .sorted(Comparator.comparing(AttendanceHistoryResponse.StudentHistoryRow::studentName))
                .toList();
        List<LocalDate> fechas = asistencias.stream().map(Attendance::getClassDate).distinct().sorted().toList();
        return new AttendanceHistoryResponse(fechas, filas);
    }

    public AttendanceHistoryResponse buildForCourseSection(Long sectionId) {
        Set<LocalDate> fechasCanceladas = classSessionRepository.findByCourseSubjectSectionId(sectionId).stream()
                .filter(cs -> cs.getStatus() == ClassSessionStatus.CANCELED)
                .map(ClassSession::getClassDate)
                .collect(Collectors.toSet());
        List<Attendance> asistencias = attendanceRepository.findByCourseSubjectSectionEnrollment_CourseSubjectSection_Id(sectionId)
                .stream().filter(a -> !fechasCanceladas.contains(a.getClassDate())).toList();
        Map<Long, List<Attendance>> porInscripcion = asistencias.stream()
                .collect(Collectors.groupingBy(a -> a.getCourseSubjectSectionEnrollment().getId()));

        Map<LocalDate, LocalDate> deadlines = deadlinePolicy.deadlinesOf(
                asistencias.stream().map(Attendance::getClassDate).toList());
        List<CourseSubjectSectionEnrollment> inscriptos =
                sectionEnrollmentFinder.findActiveInCourseSection(sectionId);
        List<AttendanceHistoryResponse.StudentHistoryRow> filas = inscriptos.stream()
                .map(enrollment -> buildRow(
                        enrollment.getStudent().getId(), enrollment.getStudent().getPerson(),
                        porInscripcion.getOrDefault(enrollment.getId(), List.of()), deadlines))
                .sorted(Comparator.comparing(AttendanceHistoryResponse.StudentHistoryRow::studentName))
                .toList();
        List<LocalDate> fechas = asistencias.stream().map(Attendance::getClassDate).distinct().sorted().toList();
        return new AttendanceHistoryResponse(fechas, filas);
    }

    private AttendanceHistoryResponse.StudentHistoryRow buildRow(
            Long studentId, Person person, List<Attendance> asistenciasDelAlumno, Map<LocalDate, LocalDate> deadlines) {
        Map<LocalDate, AttendanceHistoryResponse.HistoryCell> celdasPorFecha = asistenciasDelAlumno.stream()
                .collect(Collectors.toMap(
                        Attendance::getClassDate,
                        a -> buildCell(a, deadlines.get(a.getClassDate())),
                        (a, b) -> b));
        long presentes = asistenciasDelAlumno.stream().filter(a -> a.getStatus().countsAsPresence()).count();
        Percentage porcentaje = Percentage.ratio(presentes, asistenciasDelAlumno.size()).orElse(null);
        return new AttendanceHistoryResponse.StudentHistoryRow(
                studentId, person.getName() + " " + person.getLastname(), celdasPorFecha, porcentaje);
    }

    private AttendanceHistoryResponse.HistoryCell buildCell(Attendance attendance, LocalDate deadline) {
        boolean justifiable = attendance.getStatus() == AttendanceStatus.ABSENT && deadlinePolicy.isDeadlineOpen(deadline);
        return new AttendanceHistoryResponse.HistoryCell(
                attendance.getId(), attendance.getStatus().name(), deadline, justifiable, rejectionOf(attendance));
    }

    private AttendanceHistoryResponse.JustificationRejection rejectionOf(Attendance attendance) {
        if (attendance.getStatus() != AttendanceStatus.ABSENT || attendance.getJustificationRejectionReason() == null) {
            return null;
        }
        return new AttendanceHistoryResponse.JustificationRejection(
                attendance.getJustificationRejectionReason(), attendance.getJustificationRejectedAt());
    }
}
