package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AcademicOfferingCycleRepository extends JpaRepository<AcademicOfferingCycle, Long> {
    List<AcademicOfferingCycle> findByProgramIdOrderByCreatedAtDesc(Long programId);
    List<AcademicOfferingCycle> findByCourseIdOrderByCreatedAtDesc(Long courseId);
    List<AcademicOfferingCycle> findByStatus(AcademicOfferingCycleStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AcademicOfferingCycle c where c.id = :id")
    Optional<AcademicOfferingCycle> findByIdForUpdate(@Param("id") Long id);
}
