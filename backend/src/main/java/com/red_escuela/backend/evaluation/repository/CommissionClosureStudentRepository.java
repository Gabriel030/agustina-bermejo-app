package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionClosureStudentRepository extends JpaRepository<CommissionClosureStudent, Long> {

    List<CommissionClosureStudent> findByClosureId(Long closureId);

    Optional<CommissionClosureStudent> findByClosureIdAndEnrollmentId(Long closureId, Long enrollmentId);
}
