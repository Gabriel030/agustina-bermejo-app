package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramEnrollmentRepository extends JpaRepository<ProgramEnrollment, Long> {

    List<ProgramEnrollment> findByStudentId(Long studentId);

    List<ProgramEnrollment> findByStudentIdAndProgramIdAndStatusIn(
            Long studentId, Long programId, List<ProgramEnrollmentStatus> statuses);
}
