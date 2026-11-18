package com.red_escuela.backend.notification.broadcast.freezer.repository;

import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.domain.RecipientStatusCount;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BroadcastRecipientRepository extends JpaRepository<BroadcastRecipient, Long> {

    @Query("select r.personId from BroadcastRecipient r where r.broadcastId = :broadcastId")
    Set<Long> findPersonIdsByBroadcastId(@Param("broadcastId") Long broadcastId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BroadcastRecipient r set r.addressMasked = null, r.exclusionReason = null, r.anonymizedAt = :now "
            + "where r.broadcastId = :broadcastId and r.anonymizedAt is null")
    int anonymizeByBroadcastId(@Param("broadcastId") Long broadcastId, @Param("now") Instant now);

    long countByBroadcastId(Long broadcastId);

    long countByBroadcastIdAndStatusIn(Long broadcastId, Collection<BroadcastRecipientStatus> statuses);

    Optional<BroadcastRecipient> findByDeliveryId(Long deliveryId);

    List<BroadcastRecipient> findByBroadcastIdAndStatusIn(Long broadcastId, Collection<BroadcastRecipientStatus> statuses);

    Page<BroadcastRecipient> findByBroadcastId(Long broadcastId, Pageable pageable);

    Page<BroadcastRecipient> findByBroadcastIdAndStatus(Long broadcastId, BroadcastRecipientStatus status, Pageable pageable);

    @Query("select new com.red_escuela.backend.notification.broadcast.freezer.domain.RecipientStatusCount(r.status, count(r)) "
            + "from BroadcastRecipient r where r.broadcastId = :broadcastId group by r.status")
    List<RecipientStatusCount> countByStatus(@Param("broadcastId") Long broadcastId);
}
