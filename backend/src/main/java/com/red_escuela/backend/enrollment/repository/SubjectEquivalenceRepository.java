package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubjectEquivalenceRepository extends JpaRepository<SubjectEquivalence, Long> {

    List<SubjectEquivalence> findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(Long programEnrollmentId);

    List<SubjectEquivalence> findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(Long programEnrollmentId, SubjectEquivalenceStatus status);

    Optional<SubjectEquivalence> findByProgramEnrollmentIdAndSubjectIdAndStatus(Long programEnrollmentId, Long subjectId, SubjectEquivalenceStatus status);

    @Query("select e from SubjectEquivalence e where e.programEnrollment.id in :programEnrollmentIds and e.status = :status")
    List<SubjectEquivalence> findByProgramEnrollmentIdsAndStatus(@Param("programEnrollmentIds") Collection<Long> programEnrollmentIds, @Param("status") SubjectEquivalenceStatus status);
}
