package com.red_escuela.backend.notification.repository;

import com.red_escuela.backend.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    Optional<Notification> findFirstByTypeAndRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(NotificationType type, String relatedEntityType, Long relatedEntityId);

    @Modifying
    @Query("delete from Notification n where n.relatedEntityType = :type and n.relatedEntityId = :id and n.read = false")
    int deleteUnreadByRelatedEntity(@Param("type") String type, @Param("id") Long id);
}
