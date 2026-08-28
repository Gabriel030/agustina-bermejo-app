package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByProgramId(Long programId);

    List<Promotion> findByCourseId(Long courseId);
}
