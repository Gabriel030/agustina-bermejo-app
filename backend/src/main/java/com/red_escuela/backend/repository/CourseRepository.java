package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.StatusType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByIsActive(StatusType status);

    // Ver SubjectSectionRepository.findByIdForUpdate: mismo propósito, ahora a nivel de
    // Course.capacity (el cupo del curso como conjunto, no el de una comisión puntual).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

    // CourseEnrollmentWaitlistExpiryJob solo necesita revisar los cursos que configuraron un
    // corte — el resto tiene lista de espera abierta indefinidamente.
    List<Course> findByWaitlistDeadlineOffsetDaysIsNotNull();
}
