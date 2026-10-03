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

    private static final LocalDate TERM_START = LocalDate.of(2026, 3, 2);
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
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.IN_PROGRESS)
                .collect(Collectors.groupingBy(
                        enrollment -> enrollment.getSubjectSection().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Set<Long> riskyEnrollments = pickRiskyEnrollments(dataset);

        for (Map.Entry<Long, List<SubjectSectionEnrollment>> entry : bySection.entrySet()) {
            SubjectSection section = entry.getValue().get(0).getSubjectSection();
            Instructor instructor = dataset.getSectionInstructors().get(section.getId());
            if (instructor == null) {
                continue;
            }
            boolean isDemoTeacher = instructor.equals(dataset.getDemoInstructor());
            for (LocalDate date : sessionDates(section.getId())) {
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

                for (SubjectSectionEnrollment enrollment : entry.getValue()) {
                    Attendance attendance = new Attendance();
                    attendance.setProgramSubjectSectionEnrollment(enrollment);
                    attendance.setClassDate(date);
                    attendance.setStatus(statusFor(riskyEnrollments.contains(enrollment.getId())));
                    attendance.setRecordedBy(instructor);
                    Attendance saved = attendanceRepository.save(attendance);
                    if (isDemoTeacher && demoTeacherRecords.size() < 12) {
                        demoTeacherRecords.add(saved);
                    }
                }
            }
        }
    }

    private Set<Long> pickRiskyEnrollments(DemoDataset dataset) {
        Set<Long> risky = new HashSet<>();
        List<SubjectSectionEnrollment> running = dataset.getSectionEnrollments().stream()
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.IN_PROGRESS)
                .toList();
        for (int i = 0; i < running.size(); i += 17) {
            risky.add(running.get(i).getId());
        }
        return risky;
    }

    private void seedCourseSections(DemoDataset dataset) {
        Map<Long, List<CourseSubjectSectionEnrollment>> bySection = dataset.getCourseSectionEnrollments().stream()
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
            for (LocalDate date : sessionDates(section.getId())) {
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

                for (CourseSubjectSectionEnrollment enrollment : entry.getValue()) {
                    Attendance attendance = new Attendance();
                    attendance.setCourseSubjectSectionEnrollment(enrollment);
                    attendance.setClassDate(date);
                    attendance.setStatus(statusFor(false));
                    attendance.setRecordedBy(instructor);
                    attendanceRepository.save(attendance);
                }
            }
        }
    }

    private List<LocalDate> sessionDates(long sectionId) {
        DayOfWeek day = DayOfWeek.of(1 + (int) (sectionId % 5));
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = TERM_START;
        while (cursor.getDayOfWeek() != day) {
            cursor = cursor.plusDays(1);
        }
        while (!cursor.isAfter(TODAY)) {
            if (!holidays.contains(cursor)) {
                dates.add(cursor);
            }
            cursor = cursor.plusWeeks(1);
        }
        return dates;
    }

    private AttendanceStatus statusFor(boolean risky) {
        int roll = random.nextInt(100);
        if (risky) {
            if (roll < 40) return AttendanceStatus.ABSENT;
            if (roll < 48) return AttendanceStatus.JUSTIFIED;
            return AttendanceStatus.PRESENT;
        }
        if (roll < 8) return AttendanceStatus.ABSENT;
        if (roll < 12) return AttendanceStatus.LATE;
        if (roll < 14) return AttendanceStatus.JUSTIFICATION_PENDING;
        if (roll < 16) return AttendanceStatus.JUSTIFIED;
        return AttendanceStatus.PRESENT;
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
