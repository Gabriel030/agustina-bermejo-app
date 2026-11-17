package com.red_escuela.backend.notification.broadcast.draft.repository;

import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {
}
