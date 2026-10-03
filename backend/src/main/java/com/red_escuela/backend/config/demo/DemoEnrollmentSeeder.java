package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.config.demo.DemoCatalog.CourseSpec;
import com.red_escuela.backend.config.demo.DemoCatalog.ProgramSpec;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.evaluation.entity.FinalGradeType;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@Profile("local & demo")
public class DemoEnrollmentSeeder {

    private record Allocation(String programName, int startYear, ProgramEnrollmentStatus status) {}

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionEnrollmentRepository sectionEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSectionEnrollmentRepository;
    private final ProgramEnrollmentApplicationRepository applicationRepository;

    private final Random random = new Random(42);

    public DemoEnrollmentSeeder(
            ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionEnrollmentRepository sectionEnrollmentRepository,
            CourseEnrollmentRepository courseEnrollmentRepository,
            CourseSubjectSectionEnrollmentRepository courseSectionEnrollmentRepository,
            ProgramEnrollmentApplicationRepository applicationRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.sectionEnrollmentRepository = sectionEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.courseSectionEnrollmentRepository = courseSectionEnrollmentRepository;
        this.applicationRepository = applicationRepository;
    }

    public void seed(DemoDataset dataset) {
        List<Allocation> allocations = buildAllocations();
        List<Student> students = dataset.getStudents();
        for (int i = 0; i < allocations.size() && i < students.size(); i++) {
            Allocation allocation = allocations.get(i);
            if (allocation.programName() == null) {
                continue;
            }
            enrollInProgram(dataset, students.get(i), allocation);
        }
        enrollInCourses(dataset);
        createApplications(dataset);
    }

    private List<Allocation> buildAllocations() {
        List<Allocation> allocations = new ArrayList<>();
        allocations.add(new Allocation(DemoCatalog.EARLY_CHILDHOOD, 2024, ProgramEnrollmentStatus.ENROLLED));
        addMany(allocations, DemoCatalog.EARLY_CHILDHOOD, 2024, 2, ProgramEnrollmentStatus.ENROLLED);
        allocations.add(new Allocation(DemoCatalog.EARLY_CHILDHOOD, 2023, ProgramEnrollmentStatus.COMPLETED));
        allocations.add(new Allocation(DemoCatalog.EARLY_CHILDHOOD, 2023, ProgramEnrollmentStatus.ENROLLED));
        addMany(allocations, DemoCatalog.EARLY_CHILDHOOD, 2025, 2, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, DemoCatalog.EARLY_CHILDHOOD, 2026, 2, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, DemoCatalog.SYSTEMS_ANALYSIS, 2023, 2, ProgramEnrollmentStatus.ENROLLED);
        allocations.add(new Allocation(DemoCatalog.SYSTEMS_ANALYSIS, 2024, ProgramEnrollmentStatus.ENROLLED));
        allocations.add(new Allocation(DemoCatalog.SYSTEMS_ANALYSIS, 2024, ProgramEnrollmentStatus.WITHDRAWN));
        addMany(allocations, DemoCatalog.SYSTEMS_ANALYSIS, 2025, 2, ProgramEnrollmentStatus.ENROLLED);
        allocations.add(new Allocation(DemoCatalog.SYSTEMS_ANALYSIS, 2025, ProgramEnrollmentStatus.PAUSED));
        addMany(allocations, DemoCatalog.SYSTEMS_ANALYSIS, 2026, 2, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, DemoCatalog.PRIMARY_TEACHING, 2026, 5, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, DemoCatalog.BUSINESS_ADMIN, 2026, 4, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, DemoCatalog.NURSING, 2026, 4, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, DemoCatalog.ENVIRONMENTAL, 2026, 4, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, DemoCatalog.EDUCATION_MANAGEMENT, 2026, 4, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, null, 2026, 4, ProgramEnrollmentStatus.ENROLLED);
        return allocations;
    }

    private void addMany(List<Allocation> target, String programName, int startYear, int count, ProgramEnrollmentStatus status) {
        for (int i = 0; i < count; i++) {
            target.add(new Allocation(programName, startYear, status));
        }
    }

    private enum Level { STRONG, AVERAGE, WEAK }

    private static final Map<String, DemoOutcome> DEMO_STUDENT_HISTORY = Map.ofEntries(
            Map.entry("Pedagogía|2024", DemoOutcome.FAILED_COURSEWORK),
            Map.entry("Didáctica General|2024", DemoOutcome.FINAL_PASSED),
            Map.entry("Psicología Educacional|2024", DemoOutcome.PROMOTION),
            Map.entry("Alfabetización Académica|2024", DemoOutcome.FINAL_FAILED),
            Map.entry("Práctica Docente I|2024", DemoOutcome.FINAL_PASSED),
            Map.entry("Historia de la Educación Argentina|2024", DemoOutcome.FINAL_PASSED),
            Map.entry("Pedagogía|2025", DemoOutcome.PROMOTION),
            Map.entry("Alfabetización Académica|2025", DemoOutcome.FINAL_PASSED),
            Map.entry("Didáctica del Nivel Inicial|2025", DemoOutcome.PROMOTION),
            Map.entry("Literatura Infantil|2025", DemoOutcome.REGULAR_PENDING),
            Map.entry("Expresión Corporal y Musical|2025", DemoOutcome.PROMOTION),
            Map.entry("Sujeto de la Educación Inicial|2025", DemoOutcome.FAILED_COURSEWORK),
            Map.entry("Práctica Docente II|2025", DemoOutcome.FINAL_PASSED),
            Map.entry("Problemáticas Contemporáneas de la Educación|2025", DemoOutcome.FINAL_PASSED),
            Map.entry("Juego y Desarrollo Infantil|2026", DemoOutcome.AT_RISK),
            Map.entry("Matemática en el Nivel Inicial|2026", DemoOutcome.STRUGGLING));

    private void enrollInProgram(DemoDataset dataset, Student student, Allocation allocation) {
        Program program = dataset.program(allocation.programName());
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(allocation.status());
        enrollment.setEnrollmentDate(LocalDate.of(allocation.startYear(), 3, 5).atStartOfDay());
        if (allocation.status() == ProgramEnrollmentStatus.COMPLETED) {
            enrollment.setCompletionDate(TODAY.minusDays(39).atTime(10, 0));
        }
        ProgramEnrollment saved = programEnrollmentRepository.save(enrollment);
        dataset.getProgramEnrollments().add(saved);

        if (allocation.status() == ProgramEnrollmentStatus.WITHDRAWN) {
            return;
        }
        boolean demoStudent = student.getId().equals(dataset.getDemoStudent().getId());
        boolean graduating = allocation.startYear() == 2023;
        Level level = graduating || demoStudent ? Level.STRONG : levelFor(random.nextInt(100));
        ProgramSpec spec = specFor(allocation.programName());
        Set<String> planSubjects = new HashSet<>();
        spec.subjectsByYear().forEach(planSubjects::addAll);

        Set<String> approved = new HashSet<>();
        Map<String, Integer> attempts = new HashMap<>();
        List<String> carried = new ArrayList<>();
        for (int year = allocation.startYear(); year <= 2026; year++) {
            int curricularYear = year - allocation.startYear() + 1;
            List<String> toTake = new ArrayList<>(carried);
            carried.clear();
            if (curricularYear <= spec.totalYears()) {
                toTake.addAll(spec.subjectsByYear().get(curricularYear - 1));
            }
            if (toTake.isEmpty()) {
                break;
            }
            for (String subjectName : toTake) {
                String prerequisite = DemoCatalog.prerequisiteOf(subjectName);
                if (prerequisite != null && planSubjects.contains(prerequisite) && !approved.contains(prerequisite)) {
                    carried.add(subjectName);
                    continue;
                }
                SubjectSection section = dataset.section(allocation.programName(), subjectName, year);
                if (section == null) {
                    continue;
                }
                int attempt = attempts.merge(subjectName, 1, Integer::sum);
                DemoOutcome planned = demoStudent
                        ? DEMO_STUDENT_HISTORY.get(subjectName + "|" + year)
                        : null;
                DemoOutcome outcome = adaptToScheme(section,
                        planned != null ? planned : pickOutcome(level, year, attempt), year);
                if (planned == null && !graduating && outcome == DemoOutcome.FINAL_PASSED
                        && year == 2025 && random.nextInt(100) < 40) {
                    outcome = DemoOutcome.REGULAR_PENDING;
                }
                SubjectSectionEnrollment sectionEnrollment = createSectionEnrollment(
                        dataset, student, saved, section, year, attempt, allocation.status(), outcome);
                if (sectionEnrollment.getStatus() == EnrollmentStatus.WITHDRAWN) {
                    continue;
                }
                if (outcome.approves()) {
                    approved.add(subjectName);
                } else if (outcome.fails()) {
                    carried.add(subjectName);
                }
            }
        }
    }

    private Level levelFor(int roll) {
        if (roll < 35) return Level.STRONG;
        if (roll < 80) return Level.AVERAGE;
        return Level.WEAK;
    }

    private DemoOutcome pickOutcome(Level level, int year, int attempt) {
        int roll = random.nextInt(100);
        if (year == 2026) {
            return switch (level) {
                case STRONG -> DemoOutcome.ON_TRACK;
                case AVERAGE -> roll < 80 ? DemoOutcome.ON_TRACK : roll < 95 ? DemoOutcome.STRUGGLING : DemoOutcome.AT_RISK;
                case WEAK -> roll < 50 ? DemoOutcome.ON_TRACK : roll < 85 ? DemoOutcome.STRUGGLING : DemoOutcome.AT_RISK;
            };
        }
        if (level == Level.STRONG || attempt > 1) {
            return roll < 65 ? DemoOutcome.PROMOTION : DemoOutcome.FINAL_PASSED;
        }
        DemoOutcome outcome;
        if (level == Level.AVERAGE) {
            outcome = roll < 40 ? DemoOutcome.PROMOTION
                    : roll < 80 ? DemoOutcome.FINAL_PASSED
                    : roll < 88 ? DemoOutcome.FINAL_FAILED
                    : roll < 95 ? DemoOutcome.FAILED_COURSEWORK
                    : DemoOutcome.REGULAR_PENDING;
        } else {
            outcome = roll < 15 ? DemoOutcome.PROMOTION
                    : roll < 55 ? DemoOutcome.FINAL_PASSED
                    : roll < 70 ? DemoOutcome.FINAL_FAILED
                    : roll < 90 ? DemoOutcome.FAILED_COURSEWORK
                    : DemoOutcome.REGULAR_PENDING;
        }
        if (outcome == DemoOutcome.REGULAR_PENDING && year != 2025) {
            return DemoOutcome.FINAL_PASSED;
        }
        return outcome;
    }

    private DemoOutcome adaptToScheme(SubjectSection section, DemoOutcome outcome, int year) {
        if (outcome.running()) {
            return outcome;
        }
        boolean promotional = section.getEvaluationScheme().getPromotionMinGrade() != null
                && !Boolean.TRUE.equals(section.getEvaluationScheme().getHasFinal());
        if (promotional) {
            return switch (outcome) {
                case FINAL_PASSED, REGULAR_PENDING -> DemoOutcome.PROMOTION;
                case FINAL_FAILED -> DemoOutcome.FAILED_COURSEWORK;
                default -> outcome;
            };
        }
        if (outcome == DemoOutcome.PROMOTION) {
            return DemoOutcome.FINAL_PASSED;
        }
        if (outcome == DemoOutcome.REGULAR_PENDING && year != 2025) {
            return DemoOutcome.FINAL_PASSED;
        }
        return outcome;
    }

    private SubjectSectionEnrollment createSectionEnrollment(
            DemoDataset dataset,
            Student student,
            ProgramEnrollment programEnrollment,
            SubjectSection section,
            int year,
            int attempt,
            ProgramEnrollmentStatus programStatus,
            DemoOutcome outcome) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setEnrolledAt(LocalDate.of(year, 3, 10).atStartOfDay());
        enrollment.setAttemptNumber(attempt);
        boolean paused = year == 2026 && programStatus == ProgramEnrollmentStatus.PAUSED;
        enrollment.setStatus(paused ? EnrollmentStatus.WITHDRAWN : EnrollmentStatus.IN_PROGRESS);
        if (paused) {
            enrollment.setDroppedAt(LocalDate.of(2026, 5, 20).atStartOfDay());
        }
        SubjectSectionEnrollment saved = sectionEnrollmentRepository.save(enrollment);
        dataset.getSectionEnrollments().add(saved);
        if (!paused) {
            dataset.getOutcomes().put(saved.getId(), outcome);
        }
        return saved;
    }

    private ProgramSpec specFor(String programName) {
        return DemoCatalog.PROGRAMS.stream()
                .filter(spec -> spec.name().equals(programName))
                .findFirst()
                .orElseThrow();
    }

    private void enrollInCourses(DemoDataset dataset) {
        Map<String, List<Integer>> assignments = Map.of(
                "Primeros Auxilios y RCP", List.of(0, 5, 9, 14, 22, 30),
                "Introducción a la Programación", List.of(38, 39, 12, 17, 25),
                "Marketing Digital", List.of(40, 20, 27, 33),
                "Auxiliar en Farmacia", List.of(28, 29, 36),
                "Operador de Redes y Soporte IT", List.of(37, 10, 19, 24));

        for (CourseSpec spec : DemoCatalog.COURSES) {
            List<Integer> studentIndexes = assignments.get(spec.name());
            if (studentIndexes == null) {
                continue;
            }
            Course course = dataset.course(spec.name());
            for (Integer index : studentIndexes) {
                if (index >= dataset.getStudents().size()) {
                    continue;
                }
                Student student = dataset.getStudents().get(index);
                CourseEnrollment enrollment = new CourseEnrollment();
                enrollment.setStudent(student);
                enrollment.setCourse(course);
                enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
                enrollment.setEnrollmentDate(LocalDate.of(2026, 3, 8).atStartOfDay());
                CourseEnrollment savedEnrollment = courseEnrollmentRepository.save(enrollment);
                dataset.getCourseEnrollments().add(savedEnrollment);

                for (String subjectName : spec.subjects()) {
                    CourseSubjectSection section = dataset.courseSection(spec.name(), subjectName);
                    CourseSubjectSectionEnrollment sectionEnrollment = new CourseSubjectSectionEnrollment();
                    sectionEnrollment.setStudent(student);
                    sectionEnrollment.setCourseEnrollment(savedEnrollment);
                    sectionEnrollment.setCourseSubjectSection(section);
                    sectionEnrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
                    sectionEnrollment.setEnrolledAt(LocalDate.of(2026, 3, 10).atStartOfDay());
                    sectionEnrollment.setAttemptNumber(1);
                    CourseSubjectSectionEnrollment savedSection = courseSectionEnrollmentRepository.save(sectionEnrollment);
                    dataset.getCourseSectionEnrollments().add(savedSection);
                    dataset.getCourseOutcomes().put(savedSection.getId(), courseOutcome(spec));
                }
            }
        }
    }

    private DemoOutcome courseOutcome(CourseSpec spec) {
        int roll = random.nextInt(100);
        boolean finished = !courseEnd(spec).isAfter(TODAY);
        if (finished) {
            return roll < 85 ? DemoOutcome.PROMOTION : DemoOutcome.FAILED_COURSEWORK;
        }
        return roll < 80 ? DemoOutcome.ON_TRACK : DemoOutcome.STRUGGLING;
    }

    static LocalDate courseStart() {
        return LocalDate.of(2026, 3, 9);
    }

    static LocalDate courseEnd(CourseSpec spec) {
        return courseStart().plusMonths(spec.durationMonths()).minusDays(3);
    }

    private void createApplications(DemoDataset dataset) {
        List<Student> students = dataset.getStudents();
        createApplication(dataset, dataset.getDemoStudent(), DemoCatalog.BUSINESS_ADMIN, ProgramEnrollmentApplicationStatus.PENDING, 7);
        createApplication(dataset, students.get(38), DemoCatalog.NURSING, ProgramEnrollmentApplicationStatus.PENDING, 12);
        createApplication(dataset, students.get(39), DemoCatalog.BUSINESS_ADMIN, ProgramEnrollmentApplicationStatus.PENDING, 9);
        createApplication(dataset, students.get(40), DemoCatalog.SYSTEMS_ANALYSIS, ProgramEnrollmentApplicationStatus.PENDING, 5);
        createApplication(dataset, students.get(35), DemoCatalog.EDUCATION_MANAGEMENT, ProgramEnrollmentApplicationStatus.PENDING, 3);
        createApplication(dataset, students.get(31), DemoCatalog.ENVIRONMENTAL, ProgramEnrollmentApplicationStatus.PENDING, 1);
        createApplication(dataset, students.get(18), DemoCatalog.PRIMARY_TEACHING, ProgramEnrollmentApplicationStatus.APPROVED, 200);
        createApplication(dataset, students.get(23), DemoCatalog.BUSINESS_ADMIN, ProgramEnrollmentApplicationStatus.APPROVED, 198);
        createApplication(dataset, students.get(26), DemoCatalog.NURSING, ProgramEnrollmentApplicationStatus.REJECTED, 190);
    }

    private void createApplication(
            DemoDataset dataset, Student student, String programName,
            ProgramEnrollmentApplicationStatus status, int daysAgo) {
        ProgramEnrollmentApplication application = new ProgramEnrollmentApplication();
        application.setStudent(student);
        application.setProgram(dataset.program(programName));
        application.setStatus(status);
        LocalDateTime requestedAt = TODAY.minusDays(daysAgo).atTime(10, 30);
        application.setRequestedAt(requestedAt);
        if (status != ProgramEnrollmentApplicationStatus.PENDING) {
            application.setDecidedBy(dataset.administrativeAt(0));
            application.setDecidedAt(requestedAt.plusDays(2));
            if (status == ProgramEnrollmentApplicationStatus.REJECTED) {
                application.setRejectionReason("Falta el título secundario legalizado");
            }
        }
        applicationRepository.save(application);
    }
}
