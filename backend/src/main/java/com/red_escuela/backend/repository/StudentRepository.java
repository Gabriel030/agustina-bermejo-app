package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    // Get by ID only if active (active means linked person.status = true)
    Optional<Student> findByIdAndPerson_StatusTrue(Long id);

    // Get by DNI only if active
    Optional<Student> findByPerson_DniAndPerson_StatusTrue(String dni);

    // Get by File/Legajo
    Optional<Student> findByPerson_File(String file);

    // Logical delete: set person.status = false for the student's person
    @Modifying
    @Transactional
    @Query(value = "UPDATE person SET status = false WHERE id = (SELECT person_id FROM student WHERE id = :studentId)", nativeQuery = true)
    int logicalDeleteById(@Param("studentId") Long studentId);

    // Hard delete: delete student row (relations should be handled via FK ON DELETE CASCADE or JPA cascade)
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM student WHERE id = :studentId", nativeQuery = true)
    int hardDeleteById(@Param("studentId") Long studentId);
}
