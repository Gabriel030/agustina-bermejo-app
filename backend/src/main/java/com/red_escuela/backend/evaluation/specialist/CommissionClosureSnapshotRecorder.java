package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.ClosureSnapshotResult;
import com.red_escuela.backend.evaluation.domain.ClosureStudentAssessment;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.exception.ClosureSnapshotNotRecalculableException;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommissionClosureSnapshotRecorder {

    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureStudentRepository closureStudentRepository;
    private final ClosureRequirementsCalculator requirementsCalculator;

    public CommissionClosureSnapshotRecorder(CommissionClosureRepository closureRepository,
            CommissionClosureStudentRepository closureStudentRepository, ClosureRequirementsCalculator requirementsCalculator) {
        this.closureRepository = closureRepository;
        this.closureStudentRepository = closureStudentRepository;
        this.requirementsCalculator = requirementsCalculator;
    }

    @Transactional
    public ClosureSnapshotResult recordSnapshot(Long closureId) {
        CommissionClosure closure = closureRepository.findByIdForUpdate(closureId)
                .orElseThrow(() -> new IllegalArgumentException("Cierre inexistente: " + closureId));
        if (closure.getStatus() != ClosureStatus.DRAFT_AVAILABLE && closure.getStatus() != ClosureStatus.REOPENED) {
            throw new ClosureSnapshotNotRecalculableException(closureId);
        }
        List<ClosureStudentAssessment> assessments = requirementsCalculator.calculate(closure.getSectionKind(), closure.getSectionId());
        Map<Long, CommissionClosureStudent> existing = closureStudentRepository.findByClosureId(closureId).stream()
                .collect(Collectors.toMap(CommissionClosureStudent::getEnrollmentId, Function.identity()));
        Set<Long> assessedIds = assessments.stream().map(ClosureStudentAssessment::enrollmentId).collect(Collectors.toSet());
        closureStudentRepository.deleteAll(existing.values().stream()
                .filter(row -> !assessedIds.contains(row.getEnrollmentId())
                        || (row.getTeacherObservation() == null && isPending(assessments, row.getEnrollmentId())))
                .toList());
        closureStudentRepository.saveAll(assessments.stream()
                .filter(a -> a.finalCondition().isPresent() || existing.get(a.enrollmentId()) != null
                        && existing.get(a.enrollmentId()).getTeacherObservation() != null)
                .map(assessment -> snapshotRowOf(closureId, assessment, existing.get(assessment.enrollmentId())))
                .toList());
        List<Long> pending = assessments.stream().filter(a -> a.finalCondition().isEmpty())
                .map(ClosureStudentAssessment::enrollmentId).toList();
        return new ClosureSnapshotResult(assessments, pending);
    }

    private CommissionClosureStudent snapshotRowOf(
            Long closureId, ClosureStudentAssessment assessment, CommissionClosureStudent previous) {
        return CommissionClosureStudent.builder()
                .id(previous == null ? null : previous.getId())
                .closureId(closureId)
                .enrollmentId(assessment.enrollmentId())
                .finalCondition(assessment.finalCondition().orElse(null))
                .promotionGrade(assessment.promotionGrade())
                .attendanceOk(assessment.attendanceOk())
                .gradeOk(assessment.gradeOk())
                .administrativeOk(assessment.administrativeOk())
                .documentsComplete(assessment.documentsComplete())
                .correlativesOk(assessment.correlativesOk())
                .teacherObservation(previous == null ? null : previous.getTeacherObservation())
                .build();
    }

    private boolean isPending(List<ClosureStudentAssessment> assessments, Long enrollmentId) {
        return assessments.stream().anyMatch(a -> a.enrollmentId().equals(enrollmentId) && a.finalCondition().isEmpty());
    }
}
