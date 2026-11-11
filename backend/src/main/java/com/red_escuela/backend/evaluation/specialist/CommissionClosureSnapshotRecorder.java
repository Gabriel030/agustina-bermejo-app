package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.ClosureSnapshotResult;
import com.red_escuela.backend.evaluation.domain.ClosureStudentAssessment;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
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
        CommissionClosure closure = closureRepository.findById(closureId)
                .orElseThrow(() -> new IllegalArgumentException("Cierre inexistente: " + closureId));
        List<ClosureStudentAssessment> assessments = requirementsCalculator.calculate(closure.getSectionKind(), closure.getSectionId());
        Map<Long, CommissionClosureStudent> existing = closureStudentRepository.findByClosureId(closureId).stream()
                .collect(Collectors.toMap(CommissionClosureStudent::getEnrollmentId, Function.identity()));
        List<ClosureStudentAssessment> decided = assessments.stream().filter(a -> a.finalCondition().isPresent()).toList();
        Set<Long> decidedIds = decided.stream().map(ClosureStudentAssessment::enrollmentId).collect(Collectors.toSet());
        closureStudentRepository.deleteAll(existing.values().stream()
                .filter(row -> !decidedIds.contains(row.getEnrollmentId())).toList());
        closureStudentRepository.saveAll(decided.stream()
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
                .finalCondition(assessment.finalCondition().orElseThrow())
                .promotionGrade(assessment.promotionGrade())
                .attendanceOk(assessment.attendanceOk())
                .gradeOk(assessment.gradeOk())
                .administrativeOk(assessment.administrativeOk())
                .documentsComplete(assessment.documentsComplete())
                .correlativesOk(assessment.correlativesOk())
                .teacherObservation(previous == null ? null : previous.getTeacherObservation())
                .build();
    }
}
