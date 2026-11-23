package com.red_escuela.backend.notification.invitation.repository;

import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;

public interface InvitationWaveRecipientRepository extends JpaRepository<InvitationWaveRecipient, Long> {

    interface StatusCount {
        InvitationRecipientStatus getStatus();

        long getTotal();
    }

    interface ExclusionReasonCount {
        InvitationExclusionReason getReason();
        long getTotal();
    }

    interface DeliveryCreation {
        Long getDeliveryId();
        Instant getCreatedAt();
    }
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InvitationWaveRecipient r where r.id = :id")
    Optional<InvitationWaveRecipient> findByIdForUpdate(@Param("id") Long id);

    @Query("select r.waveId from InvitationWaveRecipient r where r.id = :id")
    Optional<Long> findWaveIdById(@Param("id") Long id);

    List<InvitationWaveRecipient> findByWaveIdAndStatusAndAttemptsLessThanOrderByIdAsc(Long waveId, InvitationRecipientStatus status,
                                                                                       int attempts, Pageable pageable);

    Page<InvitationWaveRecipient> findByWaveId(Long waveId, Pageable pageable);

    Page<InvitationWaveRecipient> findByWaveIdAndStatus(Long waveId, InvitationRecipientStatus status, Pageable pageable);

    List<InvitationWaveRecipient> findByWaveIdAndStatusIn(Long waveId, Collection<InvitationRecipientStatus> statuses);

    long countByWaveIdAndStatus(Long waveId, InvitationRecipientStatus status);

    long countByWaveIdAndStatusIn(Long waveId, Collection<InvitationRecipientStatus> statuses);

    @Query("select r.status as status, count(r) as total from InvitationWaveRecipient r where r.waveId = :waveId group by r.status")
    List<StatusCount> countByStatusForWave(@Param("waveId") Long waveId);

    @Query("select r.exclusionReason as reason, count(r) as total from InvitationWaveRecipient r where r.waveId = :waveId and r.status = com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus.EXCLUDED and r.exclusionReason is not null group by r.exclusionReason")
    List<ExclusionReasonCount> countExcludedByReasonForWave(@Param("waveId") Long waveId);

    @Query("select d.id as deliveryId, d.createdAt as createdAt from NotificationDelivery d where d.id in :deliveryIds")
    List<DeliveryCreation> findDeliveryCreations(@Param("deliveryIds") Collection<Long> deliveryIds);

    @Query("select distinct r.personId from InvitationWaveRecipient r where r.personId in :personIds and r.status in :openStatuses")
    List<Long> findPersonIdsWithOpenInvitation(@Param("personIds") Collection<Long> personIds,
                                               @Param("openStatuses") Collection<InvitationRecipientStatus> openStatuses);
}
