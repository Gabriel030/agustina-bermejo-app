package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findByName(String name);

    List<Subject> findByStatus(StatusType status);

    Optional<Subject> findTopByOrderByCodeDesc();
}
