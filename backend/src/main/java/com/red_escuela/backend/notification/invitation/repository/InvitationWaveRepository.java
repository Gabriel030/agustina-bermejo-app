package com.red_escuela.backend.notification.invitation.repository;

import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import java.util.List;
import java.util.Optional;

public interface InvitationWaveRepository extends JpaRepository<InvitationWave, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from InvitationWave w where w.id = :id")
    Optional<InvitationWave> findByIdForUpdate(@Param("id") Long id);

    List<InvitationWave> findByStatusOrderByIdAsc(InvitationWaveStatus status);
}
