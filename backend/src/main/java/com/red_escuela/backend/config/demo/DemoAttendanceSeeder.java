package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("local & demo")
public class DemoAttendanceSeeder {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final LocalTime START_TIME = LocalTime.of(18, 0);
    private static final LocalTime END_TIME = LocalTime.of(20, 0);

    private final ClassSessionRepository classSessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceChangeRequestRepository changeRequestRepository;
    private final HolidayRepository holidayRepository;

    private final Random random = new Random(11);
    private Set<LocalDate> holidays = Set.of();

    public DemoAttendanceSeeder(
            ClassSessionRepository classSessionRepository,
            AttendanceRepository attendanceRepository,
            AttendanceChangeRequestRepository changeRequestRepository,
            HolidayRepository holidayRepository) {
        this.classSessionRepository = classSessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.holidayRepository = holidayRepository;
    }

    public void seed(DemoDataset dataset) {
        holidays = holidayRepository.findAll().stream()
                .map(com.red_escuela.backend.academicstructure.entity.Holiday::getDate)
                .collect(Collectors.toSet());
        List<Attendance> demoTeacherRecords = new ArrayList<>();
        seedProgramSections(dataset, demoTeacherRecords);
        seedCourseSections(dataset);
        createChangeRequests(dataset, demoTeacherRecords);
    }

    private void seedProgramSections(DemoDataset dataset, List<Attendance> demoTeacherRecords) {
        Map<Long, List<SubjectSectionEnrollment>> bySection = dataset.getSectionEnrollments().stream()
                .filter(enrollment -> dataset.getOutcomes().containsKey(enrollment.getId()))
                .collect(Collectors.groupingBy(
                        enrollment -> enrollment.getSubjectSection().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<Long, List<SubjectSectionEnrollment>> entry : bySection.entrySet()) {
            SubjectSection section = entry.getValue().get(0).getSubjectSection();
            Instructor instructor = dataset.getSectionInstructors().get(section.getId());
            if (instructor == null) {
                continue;
            }
            LocalDate termStart = section.getTerm().getStartDate().toLocalDate();
            LocalDate termEnd = section.getTerm().getEndDate().toLocalDate();
            List<LocalDate> dates = sessionDates(section.getId(), termStart, termEnd.isBefore(TODAY) ? termEnd : TODAY);
            boolean currentYear = termStart.getYear() == TODAY.getYear();
            boolean isDemoTeacher = currentYear && instructor.equals(dataset.getDemoInstructor());
            for (LocalDate date : dates) {
                ClassSession session = new ClassSession();
                session.setSubjectSection(section);
                session.setClassDate(date);
                session.setStatus(ClassSessionStatus.HELD);
                session.setTopic("Unidad " + (1 + date.getDayOfYear() % 8));
                session.setClassroom("Aula " + (3 + section.getId() % 12));
                session.setStartTime(START_TIME);
                session.setEndTime(END_TIME);
                session.setCreatedBy(instructor);
                session.setCreatedAt(date.atTime(START_TIME));
                classSessionRepository.save(session);
            }
            for (SubjectSectionEnrollment enrollment : entry.getValue()) {
                DemoOutcome outcome = dataset.getOutcomes().get(enrollment.getId());
                List<AttendanceStatus> statuses = statusesFor(outcome, dates.size());
                for (int i = 0; i < dates.size(); i++) {
                    Attendance attendance = new Attendance();
                    attendance.setProgramSubjectSectionEnrollment(enrollment);
                    attendance.setClassDate(dates.get(i));
                    attendance.setStatus(statuses.get(i));
                    attendance.setRecordedBy(instructor);
                    Attendance saved = attendanceRepository.save(attendance);
                    if (isDemoTeacher && demoTeacherRecords.size() < 12) {
                        demoTeacherRecords.add(saved);
                    }
                }
            }
        }
    }

    private void seedCourseSections(DemoDataset dataset) {
        Map<Long, List<CourseSubjectSectionEnrollment>> bySection = dataset.getCourseSectionEnrollments().stream()
                .filter(enrollment -> dataset.getCourseOutcomes().containsKey(enrollment.getId()))
                .collect(Collectors.groupingBy(
                        enrollment -> enrollment.getCourseSubjectSection().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<Long, List<CourseSubjectSectionEnrollment>> entry : bySection.entrySet()) {
            CourseSubjectSection section = entry.getValue().get(0).getCourseSubjectSection();
            Instructor instructor = dataset.getCourseSectionInstructors().get(section.getId());
            if (instructor == null) {
                continue;
            }
            DemoCatalog.CourseSpec spec = DemoCatalog.COURSES.stream()
                    .filter(c -> c.name().equals(section.getCourseSubject().getCourse().getName()))
                    .findFirst()
                    .orElseThrow();
            LocalDate end = DemoEnrollmentSeeder.courseEnd(spec);
            List<LocalDate> dates = sessionDates(section.getId(), DemoEnrollmentSeeder.courseStart(), end.isBefore(TODAY) ? end : TODAY);
            for (LocalDate date : dates) {
                ClassSession session = new ClassSession();
                session.setCourseSubjectSection(section);
                session.setClassDate(date);
                session.setStatus(ClassSessionStatus.HELD);
                session.setTopic("Clase " + (1 + date.getDayOfYear() % 10));
                session.setClassroom("Aula 20");
                session.setStartTime(START_TIME);
                session.setEndTime(END_TIME);
                session.setCreatedBy(instructor);
                session.setCreatedAt(date.atTime(START_TIME));
                classSessionRepository.save(session);
            }
            for (CourseSubjectSectionEnrollment enrollment : entry.getValue()) {
                List<AttendanceStatus> statuses = statusesFor(dataset.getCourseOutcomes().get(enrollment.getId()), dates.size());
                for (int i = 0; i < dates.size(); i++) {
                    Attendance attendance = new Attendance();
                    attendance.setCourseSubjectSectionEnrollment(enrollment);
                    attendance.setClassDate(dates.get(i));
                    attendance.setStatus(statuses.get(i));
                    attendance.setRecordedBy(instructor);
                    attendanceRepository.save(attendance);
                }
            }
        }
    }

    private List<LocalDate> sessionDates(long sectionId, LocalDate from, LocalDate to) {
        DayOfWeek day = DayOfWeek.of(1 + (int) (sectionId % 5));
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = from;
        while (cursor.getDayOfWeek() != day) {
            cursor = cursor.plusDays(1);
        }
        while (!cursor.isAfter(to)) {
            boolean winterBreak = cursor.getMonthValue() == 7 && cursor.getDayOfMonth() >= 13 && cursor.getDayOfMonth() <= 24;
            if (!holidays.contains(cursor) && !winterBreak) {
                dates.add(cursor);
            }
            cursor = cursor.plusWeeks(1);
        }
        return dates;
    }

    private List<AttendanceStatus> statusesFor(DemoOutcome outcome, int classes) {
        double absenceRate = switch (outcome) {
            case PROMOTION, ON_TRACK -> 0.03 + random.nextInt(6) / 100.0;
            case FINAL_PASSED, FINAL_FAILED, REGULAR_PENDING -> 0.06 + random.nextInt(9) / 100.0;
            case FAILED_COURSEWORK, STRUGGLING -> 0.10 + random.nextInt(9) / 100.0;
            case AT_RISK -> 0.58 + random.nextInt(8) / 100.0;
        };
        int absences = (int) Math.round(classes * absenceRate);
        int pendingJustifications = absences > 2 ? 1 : 0;
        int justified = random.nextInt(3) == 0 ? 1 : 0;
        int late = Math.max(0, (int) Math.round(classes * 0.04));
        List<AttendanceStatus> statuses = new ArrayList<>();
        for (int i = 0; i < absences - pendingJustifications; i++) statuses.add(AttendanceStatus.ABSENT);
        for (int i = 0; i < pendingJustifications; i++) statuses.add(AttendanceStatus.JUSTIFICATION_PENDING);
        for (int i = 0; i < justified; i++) statuses.add(AttendanceStatus.JUSTIFIED);
        for (int i = 0; i < late; i++) statuses.add(AttendanceStatus.LATE);
        while (statuses.size() < classes) statuses.add(AttendanceStatus.PRESENT);
        List<AttendanceStatus> trimmed = new ArrayList<>(statuses.subList(0, classes));
        java.util.Collections.shuffle(trimmed, random);
        return trimmed;
    }

    private void createChangeRequests(DemoDataset dataset, List<Attendance> records) {
        if (records.isEmpty()) {
            return;
        }
        String[] reasons = {
                "El alumno presentó certificado médico al día siguiente",
                "Error de carga: estaba presente pero llegó sobre el final",
                "Se registró ausente por error del sistema de lectura de asistencia",
                "Participó de la jornada institucional, corresponde justificar",
                "Se confundió la fila al cargar la planilla"};
        AttendanceChangeRequestStatus[] statuses = {
                AttendanceChangeRequestStatus.PENDING,
                AttendanceChangeRequestStatus.PENDING,
                AttendanceChangeRequestStatus.PENDING,
                AttendanceChangeRequestStatus.APPROVED,
                AttendanceChangeRequestStatus.REJECTED};

        for (int i = 0; i < Math.min(records.size(), statuses.length); i++) {
            Attendance attendance = records.get(i);
            AttendanceChangeRequest request = new AttendanceChangeRequest();
            request.setAttendance(attendance);
            request.setRequestedBy(dataset.getDemoInstructor());
            request.setPreviousStatus(attendance.getStatus());
            request.setRequestedStatus(AttendanceStatus.JUSTIFIED);
            request.setReason(reasons[i]);
            request.setStatus(statuses[i]);
            request.setCreatedAt(TODAY.minusDays(3L + i).atTime(9, 0));
            if (statuses[i] != AttendanceChangeRequestStatus.PENDING) {
                request.setApprovedBy(dataset.preceptorAt(0));
                request.setApprovedAt(TODAY.minusDays(2L + i).atTime(14, 30));
            }
            changeRequestRepository.save(request);
        }
    }
}
