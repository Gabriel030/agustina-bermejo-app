package com.red_escuela.backend.announcement.audience;

import com.red_escuela.backend.announcement.entity.AudienceQuantifier;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.service.audience.AttendanceResolver;
import com.red_escuela.backend.announcement.service.audience.AudienceResolver;
import com.red_escuela.backend.announcement.service.audience.CourseworkStatusResolver;
import com.red_escuela.backend.announcement.service.audience.ExamGradeResolver;
import com.red_escuela.backend.announcement.service.audience.FinalGradeResolver;
import com.red_escuela.backend.announcement.service.audience.SectionSnapshot;
import com.red_escuela.backend.announcement.service.audience.StudentSnapshot;
import com.red_escuela.backend.announcement.service.audience.TpGradeResolver;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.api.EvaluationSchemeSummary;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceResolverTest {

    private static final double MIN_APPROVAL = 4.0;

    private final CourseworkStatusResolver coursework = new CourseworkStatusResolver();
    private final ExamGradeResolver exams = new ExamGradeResolver();
    private final TpGradeResolver tps = new TpGradeResolver();
    private final FinalGradeResolver finals = new FinalGradeResolver();
    private final AttendanceResolver attendance = new AttendanceResolver();

    @Test
    void mustTakeFinalIsRegularOnlyAndExcludesThoseWhoAlreadyPassedTheFinal() {
        StudentSnapshot regular = student(EnrollmentStatus.REGULAR, null);
        StudentSnapshot passedTheFinal = student(EnrollmentStatus.APPROVED, FinalGradeType.FINAL_EXAM);
        StudentSnapshot promoted = student(EnrollmentStatus.APPROVED, FinalGradeType.PROMOTION);
        AudienceSpec spec = spec(AudienceSegmentKey.MUST_TAKE_FINAL);

        assertThat(matches(coursework, regular, spec)).isTrue();
        assertThat(matches(coursework, passedTheFinal, spec)).isFalse();
        assertThat(matches(coursework, promoted, spec)).isFalse();
    }

    @Test
    void promotedIsApprovedByPromotionOnly() {
        AudienceSpec spec = spec(AudienceSegmentKey.PROMOTED);

        assertThat(matches(coursework, student(EnrollmentStatus.APPROVED, FinalGradeType.PROMOTION), spec)).isTrue();
        assertThat(matches(coursework, student(EnrollmentStatus.APPROVED, FinalGradeType.FINAL_EXAM), spec)).isFalse();
    }

    @Test
    void examFailedWithAnyAndWithAllSelectDifferentStudents() {
        StudentSnapshot failedBoth = withExams(Map.of(1, 2.0, 2, 3.0));
        StudentSnapshot failedOnlyTheFirst = withExams(Map.of(1, 2.0, 2, 8.0));

        AudienceSpec any = instanceSpec(AudienceSegmentKey.EXAM_FAILED, "1,2", AudienceQuantifier.ANY);
        AudienceSpec all = instanceSpec(AudienceSegmentKey.EXAM_FAILED, "1,2", AudienceQuantifier.ALL);

        assertThat(matches(exams, failedBoth, any)).isTrue();
        assertThat(matches(exams, failedBoth, all)).isTrue();
        assertThat(matches(exams, failedOnlyTheFirst, any)).isTrue();
        assertThat(matches(exams, failedOnlyTheFirst, all)).isFalse();
    }

    @Test
    void anUngradedExamCountsAsNeitherFailedNorPassed() {
        StudentSnapshot ungraded = withExams(Map.of(1, 7.0));

        assertThat(matches(exams, ungraded, instanceSpec(AudienceSegmentKey.EXAM_FAILED, "2", AudienceQuantifier.ANY))).isFalse();
        assertThat(matches(exams, ungraded, instanceSpec(AudienceSegmentKey.EXAM_PASSED, "2", AudienceQuantifier.ANY))).isFalse();
        assertThat(matches(exams, ungraded, instanceSpec(AudienceSegmentKey.EXAM_MISSING, "2", AudienceQuantifier.ANY))).isTrue();
    }

    @Test
    void notSubmittingATpIsDifferentFromSubmittingAndFailingIt() {
        StudentSnapshot submittedAndFailed = withTps(Map.of(1, 2.0));
        StudentSnapshot neverSubmitted = withTps(Map.of());

        AudienceSpec failed = instanceSpec(AudienceSegmentKey.TP_FAILED, "1", AudienceQuantifier.ANY);
        AudienceSpec missing = instanceSpec(AudienceSegmentKey.TP_MISSING, "1", AudienceQuantifier.ANY);

        assertThat(matches(tps, submittedAndFailed, failed)).isTrue();
        assertThat(matches(tps, submittedAndFailed, missing)).isFalse();
        assertThat(matches(tps, neverSubmitted, failed)).isFalse();
        assertThat(matches(tps, neverSubmitted, missing)).isTrue();
    }

    @Test
    void theFinalIsSegmentedByCallNumber() {
        StudentSnapshot passedFirstCall = withFinals(Map.of(1, 7.0));
        StudentSnapshot passedSecondCall = withFinals(Map.of(1, 2.0, 2, 6.0));

        AudienceSpec firstCall = instanceSpec(AudienceSegmentKey.FINAL_PASSED, "1", AudienceQuantifier.ANY);
        AudienceSpec secondCall = instanceSpec(AudienceSegmentKey.FINAL_PASSED, "2", AudienceQuantifier.ANY);

        assertThat(matches(finals, passedFirstCall, firstCall)).isTrue();
        assertThat(matches(finals, passedFirstCall, secondCall)).isFalse();
        assertThat(matches(finals, passedSecondCall, secondCall)).isTrue();
        assertThat(matches(finals, passedSecondCall, firstCall)).isFalse();
    }

    @Test
    void theAbsenceThresholdIsInclusive() {
        StudentSnapshot withFour = withAbsences(4);
        StudentSnapshot withFive = withAbsences(5);
        AudienceSpec fiveOrMore = new AudienceSpec(AudienceSegmentKey.ABSENCES, null, null, 5);

        assertThat(matches(attendance, withFour, fiveOrMore)).isFalse();
        assertThat(matches(attendance, withFive, fiveOrMore)).isTrue();
    }

    @Test
    void noInstancesSelectedMatchesNobody() {
        StudentSnapshot anyone = withExams(Map.of(1, 2.0));
        AudienceSpec empty = instanceSpec(AudienceSegmentKey.EXAM_FAILED, null, AudienceQuantifier.ANY);

        assertThat(matches(exams, anyone, empty)).isFalse();
    }

    @Test
    void gradeSegmentsAreNotOfferedForASectionWithoutEvaluationScheme() {
        SectionSnapshot withoutScheme = new SectionSnapshot(List.of(withExams(Map.of())), null);

        assertThat(exams.availableFor(withoutScheme, AudienceSegmentKey.EXAM_FAILED)).isFalse();
        assertThat(tps.availableFor(withoutScheme, AudienceSegmentKey.TP_MISSING)).isFalse();
        assertThat(finals.availableFor(withoutScheme, AudienceSegmentKey.FINAL_PASSED)).isFalse();
    }

    private boolean matches(AudienceResolver resolver, StudentSnapshot student, AudienceSpec spec) {
        return resolver.matches(student, spec, section());
    }

    private SectionSnapshot section() {
        EvaluationSchemeSummary scheme = new EvaluationSchemeSummary(3, 3, true, MIN_APPROVAL);
        return new SectionSnapshot(List.of(), scheme);
    }

    private AudienceSpec spec(AudienceSegmentKey key) {
        return new AudienceSpec(key, null, null, null);
    }

    private AudienceSpec instanceSpec(AudienceSegmentKey key, String instances, AudienceQuantifier quantifier) {
        return new AudienceSpec(key, instances, quantifier, null);
    }

    private StudentSnapshot student(EnrollmentStatus status, FinalGradeType finalGradeType) {
        return new StudentSnapshot(new Student(), status, finalGradeType, Map.of(), Map.of(), Map.of(), 0, 0);
    }

    private StudentSnapshot withExams(Map<Integer, Double> scores) {
        return new StudentSnapshot(new Student(), EnrollmentStatus.IN_PROGRESS, null, scores, Map.of(), Map.of(), 0, 10);
    }

    private StudentSnapshot withTps(Map<Integer, Double> scores) {
        return new StudentSnapshot(new Student(), EnrollmentStatus.IN_PROGRESS, null, Map.of(), scores, Map.of(), 0, 10);
    }

    private StudentSnapshot withFinals(Map<Integer, Double> scores) {
        return new StudentSnapshot(new Student(), EnrollmentStatus.REGULAR, null, Map.of(), Map.of(), scores, 0, 10);
    }

    private StudentSnapshot withAbsences(long absences) {
        return new StudentSnapshot(new Student(), EnrollmentStatus.IN_PROGRESS, null, Map.of(), Map.of(), Map.of(), absences, 20);
    }
}
