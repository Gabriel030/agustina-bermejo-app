package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {
}
