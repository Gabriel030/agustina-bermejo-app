package com.red_escuela.backend.notification.invitation.repository;

import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationWaveRecipientRepository extends JpaRepository<InvitationWaveRecipient, Long> {

    Page<InvitationWaveRecipient> findByWaveId(Long waveId, Pageable pageable);

    Page<InvitationWaveRecipient> findByWaveIdAndStatus(Long waveId, InvitationRecipientStatus status, Pageable pageable);

    long countByWaveIdAndStatus(Long waveId, InvitationRecipientStatus status);
}
