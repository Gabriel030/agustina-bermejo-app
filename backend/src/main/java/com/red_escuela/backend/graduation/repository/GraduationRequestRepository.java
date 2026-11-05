package com.red_escuela.backend.graduation.repository;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GraduationRequestRepository extends JpaRepository<GraduationRequest, Long> {

    List<GraduationRequest> findByProgramEnrollmentId(Long programEnrollmentId);

    List<GraduationRequest> findByStatus(GraduationRequestStatus status);

    List<GraduationRequest> findByProgramEnrollmentIdAndStatusNot(Long programEnrollmentId, GraduationRequestStatus status);

    Optional<GraduationRequest> findFirstByProgramEnrollmentIdOrderByIdDesc(Long programEnrollmentId);

    boolean existsByProgramEnrollmentIdAndStatusNot(Long programEnrollmentId, GraduationRequestStatus status);
}
