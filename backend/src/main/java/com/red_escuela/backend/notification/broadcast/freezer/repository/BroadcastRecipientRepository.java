package com.red_escuela.backend.notification.broadcast.freezer.repository;

import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface BroadcastRecipientRepository extends JpaRepository<BroadcastRecipient, Long> {

    @Query("select r.personId from BroadcastRecipient r where r.broadcastId = :broadcastId")
    Set<Long> findPersonIdsByBroadcastId(@Param("broadcastId") Long broadcastId);

    long countByBroadcastId(Long broadcastId);
}
