package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreceptorAssignmentRepository extends JpaRepository<PreceptorAssignment, Long> {

    List<PreceptorAssignment> findByPersonId(Long personId);
}
