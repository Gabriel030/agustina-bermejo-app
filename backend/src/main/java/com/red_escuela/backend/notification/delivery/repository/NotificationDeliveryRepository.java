package com.red_escuela.backend.notification.delivery.repository;

import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    boolean existsByDedupKeyAndChannel(String dedupKey, DeliveryChannel channel);

    Optional<NotificationDelivery> findByDedupKeyAndChannel(String dedupKey, DeliveryChannel channel);
}
