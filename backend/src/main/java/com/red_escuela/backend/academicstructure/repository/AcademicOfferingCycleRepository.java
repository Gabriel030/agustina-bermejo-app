package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcademicOfferingCycleRepository extends JpaRepository<AcademicOfferingCycle, Long> {
    List<AcademicOfferingCycle> findByProgramIdOrderByCreatedAtDesc(Long programId);
    List<AcademicOfferingCycle> findByCourseIdOrderByCreatedAtDesc(Long courseId);
    List<AcademicOfferingCycle> findByStatus(AcademicOfferingCycleStatus status);
}
