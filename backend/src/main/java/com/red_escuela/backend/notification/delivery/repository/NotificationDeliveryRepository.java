package com.red_escuela.backend.notification.delivery.repository;

import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    String SKIP_LOCKED_TIMEOUT = "-2";

    boolean existsByDedupKeyAndChannel(String dedupKey, DeliveryChannel channel);

    Optional<NotificationDelivery> findByDedupKeyAndChannel(String dedupKey, DeliveryChannel channel);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED_TIMEOUT))
    @Query("select d from NotificationDelivery d where d.id = :id")
    Optional<NotificationDelivery> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select d.id from NotificationDelivery d
            where d.channel = :channel and d.status in :statuses and d.scheduledFor = :scheduledFor
              and (d.notBefore is null or d.notBefore <= :now) and d.id > :afterId
            order by d.id""")
    List<Long> findDispatchableIds(@Param("channel") DeliveryChannel channel, @Param("statuses") List<DeliveryStatus> statuses,
                                   @Param("scheduledFor") LocalDate scheduledFor, @Param("now") Instant now,
                                   @Param("afterId") Long afterId, Pageable pageable);

    Page<NotificationDelivery> findByChannel(DeliveryChannel channel, Pageable pageable);
}
