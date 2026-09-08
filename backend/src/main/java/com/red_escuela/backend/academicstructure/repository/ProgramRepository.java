package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.entity.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {

    List<Program> findByStatus(StatusType status);
}
