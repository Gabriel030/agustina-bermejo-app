package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.entity.ProgramEnrollmentApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramEnrollmentApplicationRepository extends JpaRepository<ProgramEnrollmentApplication, Long> {

    List<ProgramEnrollmentApplication> findByStudentIdOrderByRequestedAtDesc(Long studentId);

    List<ProgramEnrollmentApplication> findByStatusOrderByRequestedAtAsc(ProgramEnrollmentApplicationStatus status);

    Optional<ProgramEnrollmentApplication> findByStudentIdAndProgramIdAndStatus(
            Long studentId, Long programId, ProgramEnrollmentApplicationStatus status);
}
