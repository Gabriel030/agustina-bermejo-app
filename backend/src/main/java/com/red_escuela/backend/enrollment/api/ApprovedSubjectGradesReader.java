package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ApprovedCourseworkGradeRepository;
import com.red_escuela.backend.enrollment.repository.ApprovedCourseworkGradeRepository.ApprovedCourseworkGradeRow;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class ApprovedSubjectGradesReader {

    private static final Comparator<ApprovedSubjectGrade> CURRICULUM_ORDER = Comparator
            .comparing(ApprovedSubjectGrade::curricularYear, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ApprovedSubjectGrade::subjectName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(ApprovedSubjectGrade::subjectId);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ProgramProgressReader programProgressReader;
    private final ApprovedCourseworkGradeRepository approvedCourseworkGradeRepository;
    private final SubjectEquivalenceRepository subjectEquivalenceRepository;

    public ApprovedSubjectGradesReader(ProgramEnrollmentRepository programEnrollmentRepository,
            ProgramProgressReader programProgressReader,
            ApprovedCourseworkGradeRepository approvedCourseworkGradeRepository,
            SubjectEquivalenceRepository subjectEquivalenceRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.programProgressReader = programProgressReader;
        this.approvedCourseworkGradeRepository = approvedCourseworkGradeRepository;
        this.subjectEquivalenceRepository = subjectEquivalenceRepository;
    }

    public List<ApprovedSubjectGrade> listApprovedSubjectGrades(Long studentId, Long programEnrollmentId) {
        programEnrollmentRepository.findById(programEnrollmentId)
                .filter(enrollment -> enrollment.getStudent().getId().equals(studentId))
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));

        List<ApprovedSubjectView> approved = programProgressReader.listApprovedSubjectsWithSource(programEnrollmentId);
        if (approved.isEmpty()) {
            return List.of();
        }
        Map<Long, ApprovedCourseworkGradeRow> courseworkBySubject = latestCourseworkBySubject(programEnrollmentId);
        Map<Long, SubjectEquivalence> equivalenceBySubject = latestEquivalenceBySubject(programEnrollmentId);

        return approved.stream()
                .map(view -> toGrade(view, courseworkBySubject, equivalenceBySubject))
                .sorted(CURRICULUM_ORDER)
                .toList();
    }

    private ApprovedSubjectGrade toGrade(ApprovedSubjectView view, Map<Long, ApprovedCourseworkGradeRow> courseworkBySubject,
            Map<Long, SubjectEquivalence> equivalenceBySubject) {
        Long subjectId = view.subject().subjectId();
        if (view.approvalSource() == ApprovalSource.COURSEWORK) {
            ApprovedCourseworkGradeRow row = courseworkBySubject.get(subjectId);
            return gradeOf(view, row == null ? null : row.getFinalScore(), row == null ? null : row.getFinalScoreDate());
        }
        SubjectEquivalence equivalence = equivalenceBySubject.get(subjectId);
        return gradeOf(view, equivalence == null ? null : equivalence.getOriginScore(),
                equivalence == null ? null : equivalence.getRecognizedOn());
    }

    private ApprovedSubjectGrade gradeOf(ApprovedSubjectView view, Grade grade,
            LocalDate approvedOn) {
        return new ApprovedSubjectGrade(view.subject().subjectId(), view.subject().subjectName(),
                view.subject().curricularYear(), view.approvalSource(), grade, approvedOn);
    }

    private Map<Long, ApprovedCourseworkGradeRow> latestCourseworkBySubject(Long programEnrollmentId) {
        Map<Long, ApprovedCourseworkGradeRow> bySubject = new HashMap<>();
        for (ApprovedCourseworkGradeRow row : approvedCourseworkGradeRepository
                .findByProgramEnrollmentAndStatusLatestFirst(programEnrollmentId, EnrollmentStatus.APPROVED)) {
            bySubject.putIfAbsent(row.getSubjectId(), row);
        }
        return bySubject;
    }

    private Map<Long, SubjectEquivalence> latestEquivalenceBySubject(Long programEnrollmentId) {
        Map<Long, SubjectEquivalence> bySubject = new HashMap<>();
        for (SubjectEquivalence equivalence : subjectEquivalenceRepository
                .findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(programEnrollmentId, SubjectEquivalenceStatus.ACTIVE)) {
            bySubject.putIfAbsent(equivalence.getSubjectId(), equivalence);
        }
        return bySubject;
    }
}
