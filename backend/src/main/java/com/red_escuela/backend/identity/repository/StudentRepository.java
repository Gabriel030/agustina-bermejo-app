package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);

    Optional<Student> findByIdAndPerson_Status(Long id, StatusType status);

    Optional<Student> findByPerson_DniAndPerson_Status(String dni, StatusType status);

    Optional<Student> findByPerson_File(String file);

    Optional<Student> findByPerson_Id(Long personId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE person SET status = 'INACTIVE' WHERE id = (SELECT person_id FROM student WHERE id = :studentId)", nativeQuery = true)
    int logicalDeleteById(@Param("studentId") Long studentId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM student WHERE id = :studentId", nativeQuery = true)
    int hardDeleteById(@Param("studentId") Long studentId);
}
