package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    // Get by ID only if la persona vinculada tiene el status pasado por parámetro (ej. StatusType.ACTIVE)
    Optional<Student> findByIdAndPerson_Status(Long id, StatusType status);

    // Get by DNI con el mismo filtro de status
    Optional<Student> findByPerson_DniAndPerson_Status(String dni, StatusType status);

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
