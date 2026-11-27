package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import com.red_escuela.backend.academicstructure.domain.HolidayScope;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    boolean existsByDate(LocalDate date);

    boolean existsByDateAndScopeIn(LocalDate date, Collection<HolidayScope> scopes);

    boolean existsByDateAndIdNot(LocalDate date, Long id);

    long countByDateBetween(LocalDate start, LocalDate end);

    boolean existsByDateBetween(LocalDate start, LocalDate end);

    List<Holiday> findByDateBetween(LocalDate start, LocalDate end);
}
