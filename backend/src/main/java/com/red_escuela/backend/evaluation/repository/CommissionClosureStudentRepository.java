package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionClosureStudentRepository extends JpaRepository<CommissionClosureStudent, Long> {

    List<CommissionClosureStudent> findByClosureId(Long closureId);

    List<CommissionClosureStudent> findByEnrollmentId(Long enrollmentId);

    @Modifying
    @Query("delete from CommissionClosureStudent s where s.enrollmentId in :enrollmentIds")
    int deleteByEnrollmentIdIn(@Param("enrollmentIds") Collection<Long> enrollmentIds);

    Optional<CommissionClosureStudent> findByClosureIdAndEnrollmentId(Long closureId, Long enrollmentId);
}
