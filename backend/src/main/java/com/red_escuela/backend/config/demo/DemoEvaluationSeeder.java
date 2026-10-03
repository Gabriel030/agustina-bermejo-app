package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Profile("local & demo")
public class DemoEvaluationSeeder {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private final SectionEvaluationConfigRepository configRepository;
    private final GradeItemRepository gradeItemRepository;
    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardInstructorRepository examBoardInstructorRepository;
    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;

    private final Random random = new Random(7);

    public DemoEvaluationSeeder(
            SectionEvaluationConfigRepository configRepository,
            GradeItemRepository gradeItemRepository,
            ExamBoardRepository examBoardRepository,
            ExamBoardInstructorRepository examBoardInstructorRepository,
            ExamBoardRegistrationRepository examBoardRegistrationRepository) {
        this.configRepository = configRepository;
        this.gradeItemRepository = gradeItemRepository;
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
    }

    public void seed(DemoDataset dataset) {
        createEvaluationConfigs(dataset);
        createGradeItems(dataset);
        createExamBoards(dataset);
    }

    private void createEvaluationConfigs(DemoDataset dataset) {
        List<SubjectSection> currentSections = dataset.getSections().entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("|2026"))
                .map(java.util.Map.Entry::getValue)
                .toList();

        int index = 0;
        for (SubjectSection section : currentSections) {
            SectionEvaluationConfigStatus status = statusFor(dataset, section, index);
            SectionEvaluationConfig config = new SectionEvaluationConfig();
            config.setSubjectSection(section);
            config.setStatus(status);
            config.setTpCount(3);
            config.setTp1DueDate(LocalDate.of(2026, 4, 24));
            config.setTp2DueDate(LocalDate.of(2026, 6, 26));
            config.setTp3DueDate(LocalDate.of(2026, 9, 25));
            config.setExam1Date(LocalDate.of(2026, 5, 15));
            config.setExam2Date(LocalDate.of(2026, 9, 11));
            config.setFinalCall1Date(LocalDate.of(2026, 12, 2));
            config.setFinalCall2Date(LocalDate.of(2026, 12, 16));
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

    private void createGradeItems(DemoDataset dataset) {
        for (SubjectSectionEnrollment enrollment : dataset.getSectionEnrollments()) {
            if (enrollment.getStatus() == EnrollmentStatus.IN_PROGRESS) {
                saveProgramGrade(enrollment, GradeItemType.TP, 1, LocalDate.of(2026, 4, 24), score(6, 10));
                saveProgramGrade(enrollment, GradeItemType.TP, 2, LocalDate.of(2026, 6, 26), score(5, 10));
                saveProgramGrade(enrollment, GradeItemType.EXAM, 1, LocalDate.of(2026, 5, 15), score(4, 10));
                saveProgramGrade(enrollment, GradeItemType.EXAM, 2, LocalDate.of(2026, 9, 11), score(4, 10));
            } else if (enrollment.getFinalScore() != null) {
                saveProgramGrade(enrollment, GradeItemType.FINAL, 1,
                        LocalDate.of(enrollment.getEnrolledAt().getYear(), 12, 3), enrollment.getFinalScore());
            }
        }
        for (CourseSubjectSectionEnrollment enrollment : dataset.getCourseSectionEnrollments()) {
            saveCourseGrade(enrollment, GradeItemType.TP, 1, LocalDate.of(2026, 4, 18), score(6, 10));
            saveCourseGrade(enrollment, GradeItemType.EXAM, 1, LocalDate.of(2026, 6, 13), score(5, 10));
        }
    }

    private double score(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private void saveProgramGrade(
            SubjectSectionEnrollment enrollment, GradeItemType type, int sequence, LocalDate date, double value) {
        GradeItem item = new GradeItem();
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(type);
        item.setSequenceNumber(sequence);
        item.setDate(date.atTime(19, 0));
        item.setScore(value);
        item.setMaxScore(10.0);
        item.setWeight(type == GradeItemType.EXAM ? 0.3 : 0.2);
        gradeItemRepository.save(item);
    }

    private void saveCourseGrade(
            CourseSubjectSectionEnrollment enrollment, GradeItemType type, int sequence, LocalDate date, double value) {
        GradeItem item = new GradeItem();
        item.setCourseSubjectSectionEnrollment(enrollment);
        item.setType(type);
        item.setSequenceNumber(sequence);
        item.setDate(date.atTime(19, 0));
        item.setScore(value);
        item.setMaxScore(10.0);
        item.setWeight(type == GradeItemType.EXAM ? 0.4 : 0.3);
        gradeItemRepository.save(item);
    }

    private void createExamBoards(DemoDataset dataset) {
        Term term = dataset.term(2026);
        List<String> subjectNames = List.of(
                "Pedagogía", "Didáctica General", "Programación I", "Bases de Datos", "Anatomía y Fisiología");
        List<ExamBoard> boards = new ArrayList<>();
        int offset = 0;
        for (String subjectName : subjectNames) {
            ExamBoard board = new ExamBoard();
            board.setSubject(dataset.subject(subjectName));
            board.setTerm(term);
            board.setExamDate(TODAY.plusDays(14 + offset).atTime(18, 0));
            board.setClassroom("Aula " + (5 + offset));
            board.setRegistrationStart(TODAY.minusDays(5).atStartOfDay());
            board.setRegistrationEnd(TODAY.plusDays(9).atTime(23, 59));
            board.setStatus(ExamBoardStatus.SCHEDULED);
            ExamBoard saved = examBoardRepository.save(board);
            boards.add(saved);

            for (int i = 0; i < 3; i++) {
                ExamBoardInstructor member = new ExamBoardInstructor();
                member.setExamBoard(saved);
                member.setInstructor(dataset.instructorAt(offset + i));
                examBoardInstructorRepository.save(member);
            }
            offset += 3;
        }
        registerStudentsInBoards(dataset, boards);
    }

    private void registerStudentsInBoards(DemoDataset dataset, List<ExamBoard> boards) {
        for (ExamBoard board : boards) {
            List<SubjectSectionEnrollment> candidates = dataset.getSectionEnrollments().stream()
                    .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.FAILED
                            || enrollment.getFinalGradeType() != null)
                    .filter(enrollment -> enrollment.getSubjectSection().getSubject().getId()
                            .equals(board.getSubject().getId()))
                    .limit(6)
                    .toList();
            for (SubjectSectionEnrollment enrollment : candidates) {
                ExamBoardRegistration registration = new ExamBoardRegistration();
                registration.setExamBoard(board);
                registration.setProgramSubjectSectionEnrollment(enrollment);
                registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
                registration.setRegisteredAt(TODAY.minusDays(3).atTime(11, 20));
                examBoardRegistrationRepository.save(registration);
            }
        }
    }
}
