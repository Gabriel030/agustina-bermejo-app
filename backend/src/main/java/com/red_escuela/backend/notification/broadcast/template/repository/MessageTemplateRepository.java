package com.red_escuela.backend.notification.broadcast.template.repository;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.template.entity.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

    @Query("select t from MessageTemplate t where t.active = true and (:channel is null or t.channel = :channel) "
            + "and (:category is null or t.category = :category) order by t.updatedAt desc, t.id desc")
    List<MessageTemplate> findActiveByFilters(@Param("channel") ChannelType channel,
                                              @Param("category") BroadcastCategory category);

    Optional<MessageTemplate> findByIdAndActiveTrue(Long id);
}
