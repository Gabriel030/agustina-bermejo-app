package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    boolean existsByDate(LocalDate date);

    List<Holiday> findByDateBetween(LocalDate start, LocalDate end);
}
