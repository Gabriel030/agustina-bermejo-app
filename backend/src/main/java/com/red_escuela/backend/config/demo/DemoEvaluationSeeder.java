package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.config.demo.DemoCatalog.CourseSpec;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.service.GradeItemService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Component
@Profile("local & demo")
public class DemoEvaluationSeeder {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final Double EXAM_WEIGHT = 0.5;
    private static final Double TP_WEIGHT = 0.2;

    private record Assessment(GradeItemType type, int sequence, LocalDate date) {}

    private record FinalSitting(SubjectSectionEnrollment enrollment, DemoOutcome outcome, LocalDate date) {}

    private final SectionEvaluationConfigRepository configRepository;
    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardInstructorRepository examBoardInstructorRepository;
    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final GradeItemService gradeItemService;

    private final Random random = new Random(7);

    public DemoEvaluationSeeder(
            SectionEvaluationConfigRepository configRepository,
            ExamBoardRepository examBoardRepository,
            ExamBoardInstructorRepository examBoardInstructorRepository,
            ExamBoardRegistrationRepository examBoardRegistrationRepository,
            CourseEnrollmentRepository courseEnrollmentRepository,
            GradeItemService gradeItemService) {
        this.configRepository = configRepository;
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.gradeItemService = gradeItemService;
    }

    public void seed(DemoDataset dataset) {
        createEvaluationConfigs(dataset);
        List<FinalSitting> finals = new ArrayList<>();
        List<SubjectSectionEnrollment> pendingFinals = new ArrayList<>();
        gradeProgramCoursework(dataset, finals, pendingFinals);
        createPastExamBoards(dataset, finals);
        createUpcomingExamBoards(dataset, pendingFinals);
        gradeCourses(dataset);
    }

    static List<Assessment> calendarFor(EvaluationScheme scheme, int year) {
        List<Assessment> calendar = new ArrayList<>();
        if (Boolean.TRUE.equals(scheme.getHasFinal()) && scheme.getExamCountExpected() == 1) {
            calendar.add(new Assessment(GradeItemType.TP, 1, LocalDate.of(year, 4, 17)));
            calendar.add(new Assessment(GradeItemType.TP, 2, LocalDate.of(year, 6, 12)));
            calendar.add(new Assessment(GradeItemType.TP, 3, LocalDate.of(year, 9, 4)));
            calendar.add(new Assessment(GradeItemType.EXAM, 1, LocalDate.of(year, 11, 20)));
        } else if (Boolean.TRUE.equals(scheme.getHasFinal())) {
            calendar.add(new Assessment(GradeItemType.TP, 1, LocalDate.of(year, 5, 8)));
            calendar.add(new Assessment(GradeItemType.EXAM, 1, LocalDate.of(year, 6, 26)));
            calendar.add(new Assessment(GradeItemType.TP, 2, LocalDate.of(year, 9, 18)));
            calendar.add(new Assessment(GradeItemType.EXAM, 2, LocalDate.of(year, 11, 13)));
        } else {
            calendar.add(new Assessment(GradeItemType.TP, 1, LocalDate.of(year, 4, 24)));
            calendar.add(new Assessment(GradeItemType.EXAM, 1, LocalDate.of(year, 6, 26)));
            calendar.add(new Assessment(GradeItemType.TP, 2, LocalDate.of(year, 8, 28)));
            calendar.add(new Assessment(GradeItemType.TP, 3, LocalDate.of(year, 10, 23)));
            calendar.add(new Assessment(GradeItemType.EXAM, 2, LocalDate.of(year, 11, 13)));
        }
        return calendar;
    }

    private static LocalDate dateOf(List<Assessment> calendar, GradeItemType type, int sequence) {
        return calendar.stream()
                .filter(a -> a.type() == type && a.sequence() == sequence)
                .map(Assessment::date)
                .findFirst()
                .orElse(null);
    }

    private void createEvaluationConfigs(DemoDataset dataset) {
        List<SubjectSection> currentSections = dataset.getSections().entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("|2026"))
                .map(Map.Entry::getValue)
                .toList();

        int index = 0;
        for (SubjectSection section : currentSections) {
            List<Assessment> calendar = calendarFor(section.getEvaluationScheme(), 2026);
            SectionEvaluationConfigStatus status = statusFor(dataset, section, index);
            SectionEvaluationConfig config = new SectionEvaluationConfig();
            config.setSubjectSection(section);
            config.setStatus(status);
            config.setTpCount(section.getEvaluationScheme().getTpCountExpected());
            config.setTp1DueDate(dateOf(calendar, GradeItemType.TP, 1));
            config.setTp2DueDate(dateOf(calendar, GradeItemType.TP, 2));
            config.setTp3DueDate(dateOf(calendar, GradeItemType.TP, 3));
            config.setExam1Date(dateOf(calendar, GradeItemType.EXAM, 1));
            config.setExam2Date(dateOf(calendar, GradeItemType.EXAM, 2));
            config.setFinalCall1Date(LocalDate.of(2026, 12, 2));
            config.setFinalCall2Date(LocalDate.of(2027, 2, 17));
            if (status != SectionEvaluationConfigStatus.DRAFT) {
                config.setSubmittedAt(LocalDate.of(2026, 3, 20).atTime(9, 15));
            }
            if (status == SectionEvaluationConfigStatus.APPROVED || status == SectionEvaluationConfigStatus.REJECTED) {
                config.setReviewedBy(dataset.getDirector());
                config.setReviewedAt(LocalDate.of(2026, 3, 26).atTime(16, 40));
            }
            if (status == SectionEvaluationConfigStatus.REJECTED) {
                config.setRejectionReason("El segundo parcial se superpone con la semana de mesas de examen");
            }
            configRepository.save(config);
            index++;
        }
    }

    private SectionEvaluationConfigStatus statusFor(DemoDataset dataset, SubjectSection section, int index) {
        boolean ownedByDemoTeacher = dataset.getDemoInstructor()
                .equals(dataset.getSectionInstructors().get(section.getId()));
        if (ownedByDemoTeacher && index % 4 == 1) {
            return SectionEvaluationConfigStatus.PENDING_APPROVAL;
        }
        if (ownedByDemoTeacher && index % 7 == 3) {
            return SectionEvaluationConfigStatus.DRAFT;
        }
        if (index % 23 == 5) {
            return SectionEvaluationConfigStatus.PENDING_APPROVAL;
        }
        if (index % 29 == 11) {
            return SectionEvaluationConfigStatus.REJECTED;
        }
        return SectionEvaluationConfigStatus.APPROVED;
    }

    private void gradeProgramCoursework(
            DemoDataset dataset, List<FinalSitting> finals, List<SubjectSectionEnrollment> pendingFinals) {
        for (SubjectSectionEnrollment enrollment : dataset.getSectionEnrollments()) {
            DemoOutcome outcome = dataset.getOutcomes().get(enrollment.getId());
            if (outcome == null) {
                continue;
            }
            SubjectSection section = enrollment.getSubjectSection();
            EvaluationScheme scheme = section.getEvaluationScheme();
            Instructor instructor = dataset.getSectionInstructors().get(section.getId());
            int year = enrollment.getEnrolledAt().getYear();
            List<Assessment> calendar = calendarFor(scheme, year);
            List<Assessment> due = calendar.stream().filter(a -> !a.date().isAfter(TODAY)).toList();
            int failingTp = outcome == DemoOutcome.FAILED_COURSEWORK && points(scheme.getPromotionMinGrade()) != null ? 1 : -1;
            for (Assessment assessment : due) {
                double score = courseworkScore(scheme, outcome, assessment, failingTp);
                gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), instructor.getId(),
                        request(assessment, score, null));
            }
            switch (outcome) {
                case FINAL_PASSED, FINAL_FAILED -> finals.add(new FinalSitting(enrollment, outcome, finalDateFor(enrollment, outcome)));
                case REGULAR_PENDING -> pendingFinals.add(enrollment);
                default -> { }
            }
        }
    }

    private double courseworkScore(EvaluationScheme scheme, DemoOutcome outcome, Assessment assessment, int failingTp) {
        double regular = points(scheme.getMinRegularGrade());
        boolean exam = assessment.type() == GradeItemType.EXAM;
        return switch (outcome) {
            case PROMOTION -> between(points(scheme.getPromotionMinGrade()), 10);
            case FINAL_PASSED, FINAL_FAILED, REGULAR_PENDING -> exam ? between(regular, Math.min(regular + 3, 9)) : between(regular, 9);
            case FAILED_COURSEWORK -> {
                if (assessment.type() == GradeItemType.TP && assessment.sequence() == failingTp) {
                    yield between(1, regular - 2);
                }
                yield exam ? between(1, regular - 2) : between(regular, 8);
            }
            case ON_TRACK -> exam && points(scheme.getPromotionMinGrade()) != null ? between(points(scheme.getPromotionMinGrade()), 10) : between(6, 10);
            case STRUGGLING -> exam ? between(Math.max(1, regular - 2), regular + 1) : between(regular, regular + 2);
            case AT_RISK -> exam ? between(regular, regular + 2) : between(regular, regular + 3);
        };
    }

    private static Double points(com.red_escuela.backend.shared.domain.Grade grade) {
        return grade == null ? null : grade.value().doubleValue();
    }

    private double between(double min, double max) {
        int low = (int) Math.ceil(min);
        int high = (int) Math.floor(max);
        return high <= low ? low : low + random.nextInt(high - low + 1);
    }

    private CreateGradeItemRequest request(Assessment assessment, double score, Long examBoardId) {
        Double weight = assessment.type() == GradeItemType.EXAM ? EXAM_WEIGHT
                : assessment.type() == GradeItemType.TP ? TP_WEIGHT : null;
        return new CreateGradeItemRequest(assessment.type(), assessment.sequence(), weight,
                assessment.date().atTime(18, 30), java.math.BigDecimal.valueOf(score), null, examBoardId);
    }

    private LocalDate finalDateFor(SubjectSectionEnrollment enrollment, DemoOutcome outcome) {
        int year = enrollment.getEnrolledAt().getYear();
        int offset = (int) (enrollment.getSubjectSection().getSubject().getId() % 5);
        boolean february = outcome == DemoOutcome.FINAL_PASSED && random.nextInt(100) < 40;
        LocalDate date = february ? LocalDate.of(year + 1, 2, 16).plusDays(offset) : LocalDate.of(year, 12, 2).plusDays(offset);
        return date.isAfter(TODAY) ? LocalDate.of(year, 12, 2).plusDays(offset) : date;
    }

    private void createPastExamBoards(DemoDataset dataset, List<FinalSitting> finals) {
        Map<String, List<FinalSitting>> byBoard = new LinkedHashMap<>();
        for (FinalSitting sitting : finals) {
            String key = sitting.enrollment().getSubjectSection().getSubject().getId() + "|" + sitting.date();
            byBoard.computeIfAbsent(key, k -> new ArrayList<>()).add(sitting);
        }
        for (List<FinalSitting> sittings : byBoard.values()) {
            FinalSitting first = sittings.get(0);
            LocalDate examDate = first.date();
            ExamBoard board = saveBoard(dataset, first.enrollment().getSubjectSection(), examDate,
                    examDate.minusDays(25), examDate.minusDays(5));
            Set<Instructor> members = new LinkedHashSet<>();
            sittings.forEach(s -> members.add(dataset.getSectionInstructors().get(s.enrollment().getSubjectSection().getId())));
            assignTribunal(dataset, board, members);

            for (FinalSitting sitting : sittings) {
                SubjectSectionEnrollment enrollment = sitting.enrollment();
                ExamBoardRegistration registration = new ExamBoardRegistration();
                registration.setExamBoard(board);
                registration.setProgramSubjectSectionEnrollment(enrollment);
                registration.setStatus(ExamBoardRegistrationStatus.GRADED);
                registration.setRegisteredAt(examDate.minusDays(12).atTime(10, 15));
                examBoardRegistrationRepository.save(registration);

                EvaluationScheme scheme = enrollment.getSubjectSection().getEvaluationScheme();
                double score = sitting.outcome() == DemoOutcome.FINAL_PASSED
                        ? between(points(scheme.getMinApprovalGrade()), 10)
                        : between(1, points(scheme.getMinApprovalGrade()) - 1);
                Instructor grader = dataset.getSectionInstructors().get(enrollment.getSubjectSection().getId());
                gradeItemService.loadProgramEnrollmentGrade(enrollment.getId(), grader.getId(),
                        request(new Assessment(GradeItemType.FINAL, 1, examDate), score, board.getId()));
                dataset.getFinalExamDates().put(enrollment.getId(), examDate);
            }
            board.setStatus(ExamBoardStatus.CLOSED);
            examBoardRepository.save(board);
        }
    }

    private void createUpcomingExamBoards(DemoDataset dataset, List<SubjectSectionEnrollment> pendingFinals) {
        Map<Long, List<SubjectSectionEnrollment>> bySubject = new LinkedHashMap<>();
        pendingFinals.forEach(e -> bySubject.computeIfAbsent(e.getSubjectSection().getSubject().getId(), k -> new ArrayList<>()).add(e));
        int offset = 0;
        for (List<SubjectSectionEnrollment> enrollments : bySubject.values()) {
            LocalDate examDate = LocalDate.of(2026, 11, 23).plusDays(offset % 5);
            ExamBoard board = saveBoard(dataset, enrollments.get(0).getSubjectSection(), examDate,
                    TODAY.minusDays(5), examDate.minusDays(7));
            Set<Instructor> members = new LinkedHashSet<>();
            enrollments.forEach(e -> members.add(dataset.getSectionInstructors().get(e.getSubjectSection().getId())));
            assignTribunal(dataset, board, members);
            for (SubjectSectionEnrollment enrollment : enrollments) {
                boolean demoStudent = enrollment.getStudent().getId().equals(dataset.getDemoStudent().getId());
                if (demoStudent || random.nextInt(100) >= 70) {
                    continue;
                }
                ExamBoardRegistration registration = new ExamBoardRegistration();
                registration.setExamBoard(board);
                registration.setProgramSubjectSectionEnrollment(enrollment);
                registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
                registration.setRegisteredAt(TODAY.minusDays(1 + random.nextInt(4)).atTime(11, 20));
                examBoardRegistrationRepository.save(registration);
            }
            offset++;
        }
    }

    private ExamBoard saveBoard(DemoDataset dataset, SubjectSection section, LocalDate examDate,
                                LocalDate registrationStart, LocalDate registrationEnd) {
        ExamBoard board = new ExamBoard();
        board.setSubject(section.getSubject());
        board.setTerm(dataset.term(examDate.getMonthValue() <= 2 ? examDate.getYear() : examDate.getYear()));
        board.setExamDate(examDate.atTime(18, 0));
        board.setClassroom("Aula " + (2 + (int) (section.getSubject().getId() % 10)));
        board.setRegistrationStart(registrationStart.atStartOfDay());
        board.setRegistrationEnd(registrationEnd.atTime(23, 59));
        board.setStatus(ExamBoardStatus.SCHEDULED);
        return examBoardRepository.save(board);
    }

    private void assignTribunal(DemoDataset dataset, ExamBoard board, Set<Instructor> members) {
        int filler = (int) (board.getSubject().getId() % 7);
        while (members.size() < 3) {
            members.add(dataset.instructorAt(filler++));
        }
        for (Instructor instructor : members) {
            ExamBoardInstructor member = new ExamBoardInstructor();
            member.setExamBoard(board);
            member.setInstructor(instructor);
            examBoardInstructorRepository.save(member);
        }
    }

    private void gradeCourses(DemoDataset dataset) {
        Map<Long, List<CourseSubjectSectionEnrollment>> byCourseEnrollment = new LinkedHashMap<>();
        for (CourseSubjectSectionEnrollment enrollment : dataset.getCourseSectionEnrollments()) {
            DemoOutcome outcome = dataset.getCourseOutcomes().get(enrollment.getId());
            if (outcome == null) {
                continue;
            }
            CourseSubjectSection section = enrollment.getCourseSubjectSection();
            CourseSpec spec = DemoCatalog.COURSES.stream()
                    .filter(c -> c.name().equals(section.getCourseSubject().getCourse().getName()))
                    .findFirst()
                    .orElseThrow();
            Instructor instructor = dataset.getCourseSectionInstructors().get(section.getId());
            EvaluationScheme scheme = section.getEvaluationScheme();
            int failingTp = outcome == DemoOutcome.FAILED_COURSEWORK ? 2 : -1;
            for (Assessment assessment : courseCalendar(spec)) {
                if (assessment.date().isAfter(TODAY)) {
                    continue;
                }
                double score = courseworkScore(scheme, outcome, assessment, failingTp);
                gradeItemService.loadCourseEnrollmentGrade(enrollment.getId(), instructor.getId(),
                        request(assessment, score, null));
            }
            byCourseEnrollment.computeIfAbsent(enrollment.getCourseEnrollment().getId(), k -> new ArrayList<>()).add(enrollment);
        }
        for (CourseEnrollment courseEnrollment : dataset.getCourseEnrollments()) {
            List<CourseSubjectSectionEnrollment> sections = byCourseEnrollment.get(courseEnrollment.getId());
            if (sections == null) {
                continue;
            }
            CourseSpec spec = DemoCatalog.COURSES.stream()
                    .filter(c -> c.name().equals(courseEnrollment.getCourse().getName()))
                    .findFirst()
                    .orElseThrow();
            LocalDate end = DemoEnrollmentSeeder.courseEnd(spec);
            if (end.isAfter(TODAY)) {
                continue;
            }
            boolean allApproved = sections.stream().allMatch(s -> s.getStatus() == EnrollmentStatus.APPROVED);
            courseEnrollment.setStatus(allApproved ? EnrollmentStatus.APPROVED : EnrollmentStatus.FAILED);
            courseEnrollment.setCompletionDate(end.atTime(20, 0));
            courseEnrollmentRepository.save(courseEnrollment);
        }
    }

    private List<Assessment> courseCalendar(CourseSpec spec) {
        LocalDate start = DemoEnrollmentSeeder.courseStart();
        long span = ChronoUnit.DAYS.between(start, DemoEnrollmentSeeder.courseEnd(spec));
        return List.of(
                new Assessment(GradeItemType.TP, 1, start.plusDays(span * 20 / 100)),
                new Assessment(GradeItemType.EXAM, 1, start.plusDays(span * 40 / 100)),
                new Assessment(GradeItemType.TP, 2, start.plusDays(span * 55 / 100)),
                new Assessment(GradeItemType.TP, 3, start.plusDays(span * 75 / 100)),
                new Assessment(GradeItemType.EXAM, 2, start.plusDays(span * 95 / 100)));
    }
}
