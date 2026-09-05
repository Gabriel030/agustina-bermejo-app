package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.FeeSchedule;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, Long> {

    List<FeeSchedule> findAllByOrderByValidFromDesc();

    Optional<FeeSchedule> findFirstByProgramIdAndConceptAndValidFromLessThanEqualOrderByValidFromDesc(
            Long programId, FeeScheduleConcept concept, LocalDate date);

    Optional<FeeSchedule> findFirstByCourseIdAndConceptAndValidFromLessThanEqualOrderByValidFromDesc(
            Long courseId, FeeScheduleConcept concept, LocalDate date);
}
