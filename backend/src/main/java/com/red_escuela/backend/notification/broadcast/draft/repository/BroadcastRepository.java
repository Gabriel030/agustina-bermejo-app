package com.red_escuela.backend.notification.broadcast.draft.repository;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Broadcast b where b.id = :id")
    Optional<Broadcast> findByIdForUpdate(@Param("id") Long id);

    Page<Broadcast> findByStatus(BroadcastStatus status, Pageable pageable);
}
