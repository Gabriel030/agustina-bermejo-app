package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstitutionRepository extends JpaRepository<Institution, Long> {
}
