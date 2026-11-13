package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationSummaryResponse;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoardEnrollmentKind;
import com.red_escuela.backend.evaluation.entity.ExamBoardFinalResult;
import com.red_escuela.backend.evaluation.entity.ExamBoardPriorCondition;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class ExamBoardRegistrationSummaryBuilder {

    private static final String LOOSE_COURSE_LABEL = "Curso suelto";

    private final GradeItemRepository gradeItemRepository;

    public ExamBoardRegistrationSummaryBuilder(GradeItemRepository gradeItemRepository) {
        this.gradeItemRepository = gradeItemRepository;
    }

    public List<ExamBoardRegistrationSummaryResponse> build(Long examBoardId, List<ExamBoardRegistration> registrations) {
        List<GradeItem> grades = gradeItemRepository.findByExamBoardId(examBoardId);
        return registrations.stream().map(registration -> build(registration, grades)).toList();
    }

    private ExamBoardRegistrationSummaryResponse build(ExamBoardRegistration registration, List<GradeItem> grades) {
        boolean program = registration.getProgramSubjectSectionEnrollment() != null;
        Optional<GradeItem> graded = grades.stream()
                .filter(grade -> sameEnrollment(grade, registration))
                .filter(grade -> grade.getScore() != null)
                .findFirst();
        Grade finalScore = graded.map(GradeItem::getScore).orElse(null);
        EvaluationScheme scheme;
        boolean regular;
        Long enrollmentId;
        String carrera;
        Integer attemptNumber;
        Person person;
        if (program) {
            var enrollment = registration.getProgramSubjectSectionEnrollment();
            scheme = enrollment.getSubjectSection().getEvaluationScheme();
            regular = enrollment.hasRegularCoursework();
            enrollmentId = enrollment.getId();
            carrera = enrollment.getProgramEnrollment().getProgram().getName();
            attemptNumber = enrollment.getAttemptNumber();
            person = enrollment.getStudent().getPerson();
        } else {
            var enrollment = registration.getCourseSubjectSectionEnrollment();
            scheme = enrollment.getCourseSubjectSection().getEvaluationScheme();
            regular = enrollment.hasRegularCoursework();
            enrollmentId = enrollment.getId();
            carrera = LOOSE_COURSE_LABEL;
            attemptNumber = enrollment.getAttemptNumber();
            person = enrollment.getStudent().getPerson();
        }
        Grade minApprovalGrade = scheme == null ? null : scheme.getMinApprovalGrade();
        return new ExamBoardRegistrationSummaryResponse(
                registration.getId(),
                person.getName() + " " + person.getLastname(),
                person.getDni(),
                carrera,
                attemptNumber,
                registration.getStatus().name(),
                Grade.valueOrNull(finalScore),
                enrollmentId,
                program ? ExamBoardEnrollmentKind.PROGRAM : ExamBoardEnrollmentKind.COURSE,
                regular || graded.isPresent() ? ExamBoardPriorCondition.REGULAR : ExamBoardPriorCondition.FREE,
                minApprovalGrade,
                graded.map(GradeItem::getId).orElse(null),
                resultOf(registration.getStatus(), finalScore, minApprovalGrade));
    }

    private ExamBoardFinalResult resultOf(ExamBoardRegistrationStatus status, Grade finalScore, Grade minApprovalGrade) {
        if (status == ExamBoardRegistrationStatus.ABSENT) {
            return ExamBoardFinalResult.ABSENT;
        }
        if (finalScore == null || minApprovalGrade == null) {
            return null;
        }
        return finalScore.meets(minApprovalGrade) ? ExamBoardFinalResult.PASSED : ExamBoardFinalResult.FAILED;
    }

    private boolean sameEnrollment(GradeItem grade, ExamBoardRegistration registration) {
        if (registration.getProgramSubjectSectionEnrollment() != null) {
            return grade.getProgramSubjectSectionEnrollment() != null
                    && grade.getProgramSubjectSectionEnrollment().getId()
                            .equals(registration.getProgramSubjectSectionEnrollment().getId());
        }
        return grade.getCourseSubjectSectionEnrollment() != null
                && grade.getCourseSubjectSectionEnrollment().getId()
                        .equals(registration.getCourseSubjectSectionEnrollment().getId());
    }
}
