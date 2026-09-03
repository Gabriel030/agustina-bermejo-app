package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.GraduationRequest;
import com.red_escuela.backend.entity.GraduationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GraduationRequestRepository extends JpaRepository<GraduationRequest, Long> {

    List<GraduationRequest> findByProgramEnrollmentId(Long programEnrollmentId);

    List<GraduationRequest> findByStatus(GraduationRequestStatus status);

    // Resolver si ya hay un trámite activo antes del alta — red de seguridad adicional al
    // índice único parcial de la migración (decisión 11), mismo patrón que ProgramSubjectService.
    List<GraduationRequest> findByProgramEnrollmentIdAndStatusNot(Long programEnrollmentId, GraduationRequestStatus status);
}
