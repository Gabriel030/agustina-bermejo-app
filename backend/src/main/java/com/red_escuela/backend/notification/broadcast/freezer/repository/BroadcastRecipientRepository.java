package com.red_escuela.backend.notification.broadcast.freezer.repository;

import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatusCount;
import com.red_escuela.backend.notification.broadcast.freezer.domain.ExclusionReasonCount;
import com.red_escuela.backend.notification.broadcast.freezer.domain.RecipientStatusCount;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BroadcastRecipientRepository extends JpaRepository<BroadcastRecipient, Long> {

    @Query("select r.personId from BroadcastRecipient r where r.broadcastId = :broadcastId")
    Set<Long> findPersonIdsByBroadcastId(@Param("broadcastId") Long broadcastId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BroadcastRecipient r set r.addressMasked = null, r.anonymizedAt = :now "
            + "where r.broadcastId = :broadcastId and r.anonymizedAt is null")
    int anonymizeByBroadcastId(@Param("broadcastId") Long broadcastId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from broadcast_recipient where id in "
            + "(select id from broadcast_recipient where broadcast_id = :broadcastId order by id limit :chunkSize)", nativeQuery = true)
    int deleteChunkByBroadcastId(@Param("broadcastId") Long broadcastId, @Param("chunkSize") int chunkSize);

    @Query("""
            select b.id from Broadcast b
            where b.sentTotal is not null
              and ((b.status = com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus.CANCELLED and b.cancelledAt < :cutoff)
                or (b.status = com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus.COMPLETED and b.completedAt < :cutoff))
              and exists (select 1 from BroadcastRecipient r where r.broadcastId = b.id)
            order by b.id
            """)
    List<Long> findBroadcastIdsWithRecipientsPastPurgeCutoff(@Param("cutoff") Instant cutoff, Pageable pageable);

    long countByBroadcastId(Long broadcastId);

    long countByBroadcastIdAndStatusIn(Long broadcastId, Collection<BroadcastRecipientStatus> statuses);

    Optional<BroadcastRecipient> findByDeliveryId(Long deliveryId);

    List<BroadcastRecipient> findByBroadcastIdAndStatusIn(Long broadcastId, Collection<BroadcastRecipientStatus> statuses);

    Page<BroadcastRecipient> findByBroadcastId(Long broadcastId, Pageable pageable);

    Page<BroadcastRecipient> findByBroadcastIdAndStatus(Long broadcastId, BroadcastRecipientStatus status, Pageable pageable);

    @Query("select new com.red_escuela.backend.notification.broadcast.freezer.domain.RecipientStatusCount(r.status, count(r)) "
            + "from BroadcastRecipient r where r.broadcastId = :broadcastId group by r.status")
    List<RecipientStatusCount> countByStatus(@Param("broadcastId") Long broadcastId);

    @Query("select new com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatusCount(r.broadcastId, r.status, count(r)) "
            + "from BroadcastRecipient r where r.broadcastId in :broadcastIds group by r.broadcastId, r.status")
    List<BroadcastRecipientStatusCount> countByStatusForBroadcasts(@Param("broadcastIds") Collection<Long> broadcastIds);
    @Query("select new com.red_escuela.backend.notification.broadcast.freezer.domain.ExclusionReasonCount(r.exclusionReason, count(r)) "
            + "from BroadcastRecipient r where r.broadcastId = :broadcastId and r.status = com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus.EXCLUDED "
            + "group by r.exclusionReason")
    List<ExclusionReasonCount> countExcludedByReason(@Param("broadcastId") Long broadcastId);

    @Query("select r.deliveryId from BroadcastRecipient r, NotificationDelivery d "
            + "where r.status = com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus.QUEUED "
            + "and d.id = r.deliveryId and d.status not in (com.red_escuela.backend.notification.delivery.domain.DeliveryStatus.QUEUED, "
            + "com.red_escuela.backend.notification.delivery.domain.DeliveryStatus.DEFERRED) order by r.id")
    List<Long> findQueuedDeliveryIdsAlreadyResolved(org.springframework.data.domain.Pageable pageable);
}
