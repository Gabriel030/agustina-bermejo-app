package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface InstructorRepository extends JpaRepository<Instructor, Long> {
    Optional<Instructor> findByIdAndPerson_Status(Long id, StatusType status);

    Optional<Instructor> findByPerson_Id(Long personId);

    Optional<Instructor> findByPerson_DniAndPerson_Status(String dni, StatusType status);

    Optional<Instructor> findByPerson_File(String file);

    @Modifying
    @Transactional
    @Query(value = "UPDATE person SET status = 'INACTIVE' WHERE id = (SELECT person_id FROM instructor WHERE id = :instructorId)", nativeQuery = true)
    int logicalDeleteById(@Param("instructorId") Long instructorId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM instructor WHERE id = :instructorId", nativeQuery = true)
    int hardDeleteById(@Param("instructorId") Long instructorId);
}
