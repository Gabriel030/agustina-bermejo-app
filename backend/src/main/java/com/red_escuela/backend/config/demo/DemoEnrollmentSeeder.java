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
        allocations.add(new Allocation(DemoCatalog.EARLY_CHILDHOOD, 2024, ProgramEnrollmentStatus.COMPLETED));
        addMany(allocations, DemoCatalog.EARLY_CHILDHOOD, 2025, 3, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, DemoCatalog.EARLY_CHILDHOOD, 2026, 2, ProgramEnrollmentStatus.ENROLLED);
        addMany(allocations, DemoCatalog.SYSTEMS_ANALYSIS, 2024, 3, ProgramEnrollmentStatus.ENROLLED);
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

    private void enrollInProgram(DemoDataset dataset, Student student, Allocation allocation) {
        Program program = dataset.program(allocation.programName());
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(allocation.status());
        enrollment.setEnrollmentDate(LocalDate.of(allocation.startYear(), 3, 5).atStartOfDay());
        if (allocation.status() == ProgramEnrollmentStatus.COMPLETED) {
            enrollment.setCompletionDate(LocalDate.of(2026, 8, 15).atStartOfDay());
        }
        ProgramEnrollment saved = programEnrollmentRepository.save(enrollment);
        dataset.getProgramEnrollments().add(saved);

        if (allocation.status() == ProgramEnrollmentStatus.WITHDRAWN) {
            return;
        }
        ProgramSpec spec = specFor(allocation.programName());
        int curricularYear = 1;
        for (int year = allocation.startYear(); year <= 2026 && curricularYear <= spec.totalYears(); year++, curricularYear++) {
            boolean currentYear = year == 2026;
            List<String> subjects = spec.subjectsByYear().get(curricularYear - 1);
            for (String subjectName : subjects) {
                SubjectSection section = dataset.section(allocation.programName(), subjectName, year);
                if (section == null) {
                    continue;
                }
                createSectionEnrollment(dataset, student, saved, section, year, currentYear, allocation.status());
            }
        }
    }

    private void createSectionEnrollment(
            DemoDataset dataset,
            Student student,
            ProgramEnrollment programEnrollment,
            SubjectSection section,
            int year,
            boolean currentYear,
            ProgramEnrollmentStatus programStatus) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setEnrolledAt(LocalDate.of(year, 3, 10).atStartOfDay());
        enrollment.setAttemptNumber(1);
        boolean stillRunning = currentYear && programStatus != ProgramEnrollmentStatus.COMPLETED;
        if (stillRunning) {
            enrollment.setStatus(programStatus == ProgramEnrollmentStatus.PAUSED
                    ? EnrollmentStatus.WITHDRAWN
                    : EnrollmentStatus.IN_PROGRESS);
            if (programStatus == ProgramEnrollmentStatus.PAUSED) {
                enrollment.setDroppedAt(LocalDate.of(2026, 5, 20).atStartOfDay());
            }
        } else {
            boolean failed = random.nextInt(10) == 0;
            enrollment.setStatus(failed ? EnrollmentStatus.FAILED : EnrollmentStatus.APPROVED);
            double score = failed ? 2 + random.nextInt(2) : 6 + random.nextInt(5);
            enrollment.setFinalScore(score);
            enrollment.setFinalGradeType(score >= 7 ? FinalGradeType.PROMOTION : FinalGradeType.FINAL_EXAM);
        }
        dataset.getSectionEnrollments().add(sectionEnrollmentRepository.save(enrollment));
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
                    dataset.getCourseSectionEnrollments().add(courseSectionEnrollmentRepository.save(sectionEnrollment));
                }
            }
        }
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
