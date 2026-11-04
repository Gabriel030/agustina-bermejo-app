package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.entity.ShiftRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftRangeRepository extends JpaRepository<ShiftRange, SectionShift> {

    List<ShiftRange> findAllByOrderByStartTimeAsc();
}
