package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.FeeSchedule;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, Long> {

    List<FeeSchedule> findAllByOrderByVigenteDesdeDesc();

    Optional<FeeSchedule> findFirstByProgramIdAndConceptAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            Long programId, FeeScheduleConcept concept, LocalDate fecha);

    Optional<FeeSchedule> findFirstByCourseIdAndConceptAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            Long courseId, FeeScheduleConcept concept, LocalDate fecha);
}
