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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Broadcast b where b.id = :id")
    Optional<Broadcast> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select b.id from Broadcast b
            where ((b.status = com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus.CANCELLED and b.cancelledAt < :cutoff)
                or (b.status = com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus.COMPLETED and b.completedAt < :cutoff))
              and exists (select 1 from BroadcastRecipient r where r.broadcastId = b.id and r.anonymizedAt is null)
            order by b.id
            """)
    List<Long> findIdsWithRecipientDataPastRetention(@Param("cutoff") Instant cutoff, Pageable pageable);

    Page<Broadcast> findByStatus(BroadcastStatus status, Pageable pageable);
}
