package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Scholarship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScholarshipRepository extends JpaRepository<Scholarship, Long> {

    List<Scholarship> findByCourseEnrollmentId(Long courseEnrollmentId);

    List<Scholarship> findByProgramEnrollmentId(Long programEnrollmentId);
}
