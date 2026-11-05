package com.red_escuela.backend.academicstructure.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    Optional<Classroom> findByName(String name);
    List<Classroom> findByStatus(StatusType status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Classroom c where c.id in :ids order by c.id")
    List<Classroom> lockAllByIdOrdered(@Param("ids") Collection<Long> ids);
}
