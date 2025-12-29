package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Instructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface InstructorRepository extends JpaRepository<Instructor, Long> {

    Optional<Instructor> findByIdAndPerson_StatusTrue(Long id);

    Optional<Instructor> findByPerson_DniAndPerson_StatusTrue(String dni);

    Optional<Instructor> findByPerson_File(String file);

    @Modifying
    @Transactional
    @Query(value = "UPDATE person SET status = false WHERE id = (SELECT person_id FROM instructor WHERE id = :instructorId)", nativeQuery = true)
    int logicalDeleteById(@Param("instructorId") Long instructorId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM instructor WHERE id = :instructorId", nativeQuery = true)
    int hardDeleteById(@Param("instructorId") Long instructorId);
}
