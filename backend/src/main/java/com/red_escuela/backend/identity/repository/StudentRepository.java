package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    @Override
    @EntityGraph(attributePaths = "person")
    List<Student> findAll(Specification<Student> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = "person")
    Page<Student> findAll(Specification<Student> spec, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);

    @Query("select new com.red_escuela.backend.identity.repository.StudentDocumentRow(p.dni, s.id) from Student s join s.person p where p.documentType = :type and p.dni in :numbers")
    List<StudentDocumentRow> findIdsByDocumentTypeAndNumbers(@Param("type") DocumentType type, @Param("numbers") Collection<String> numbers);

    @Query(value = "select p.id from person p where p.id = any(:personIds) and p.email is not null and (exists (select 1 from student s where s.guardian_email is not null and lower(trim(s.guardian_email)) = lower(trim(p.email))) or exists (select 1 from legal_guardian_confirmation c where lower(trim(c.guardian_email)) = lower(trim(p.email))))", nativeQuery = true)
    List<Long> findPersonIdsWithEmailEqualToAnyGuardianEmail(@Param("personIds") Long[] personIds);

    Optional<Student> findByIdAndPerson_Status(Long id, StatusType status);

    Optional<Student> findByPerson_DniAndPerson_Status(String dni, StatusType status);

    Optional<Student> findByPerson_File(String file);

    Optional<Student> findByPerson_Id(Long personId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Student s where s.person.id in :personIds")
    int deleteByPersonIdIn(@Param("personIds") Collection<Long> personIds);

    @Modifying
    @Transactional
    @Query(value = "UPDATE person SET status = 'INACTIVE' WHERE id = (SELECT person_id FROM student WHERE id = :studentId)", nativeQuery = true)
    int logicalDeleteById(@Param("studentId") Long studentId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM student WHERE id = :studentId", nativeQuery = true)
    int hardDeleteById(@Param("studentId") Long studentId);
}
