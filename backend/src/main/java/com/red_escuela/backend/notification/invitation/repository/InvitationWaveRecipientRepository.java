package com.red_escuela.backend.notification.invitation.repository;

import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InvitationWaveRecipientRepository extends JpaRepository<InvitationWaveRecipient, Long> {

    interface StatusCount {
        InvitationRecipientStatus getStatus();

        long getTotal();
    }

    Page<InvitationWaveRecipient> findByWaveId(Long waveId, Pageable pageable);

    Page<InvitationWaveRecipient> findByWaveIdAndStatus(Long waveId, InvitationRecipientStatus status, Pageable pageable);

    long countByWaveIdAndStatus(Long waveId, InvitationRecipientStatus status);

    long countByWaveIdAndStatusIn(Long waveId, Collection<InvitationRecipientStatus> statuses);

    @Query("select r.status as status, count(r) as total from InvitationWaveRecipient r where r.waveId = :waveId group by r.status")
    List<StatusCount> countByStatusForWave(@Param("waveId") Long waveId);

    @Query("select distinct r.personId from InvitationWaveRecipient r where r.personId in :personIds and r.status in :openStatuses")
    List<Long> findPersonIdsWithOpenInvitation(@Param("personIds") Collection<Long> personIds,
                                               @Param("openStatuses") Collection<InvitationRecipientStatus> openStatuses);
}
