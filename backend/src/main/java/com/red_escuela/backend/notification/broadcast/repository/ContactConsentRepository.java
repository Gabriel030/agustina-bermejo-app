package com.red_escuela.backend.notification.broadcast.repository;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.entity.ContactConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContactConsentRepository extends JpaRepository<ContactConsent, Long> {

    Optional<ContactConsent> findByPersonIdAndChannelAndCategory(Long personId, ChannelType channel, BroadcastCategory category);

    List<ContactConsent> findAllByPersonId(Long personId);

    List<ContactConsent> findAllByPersonIdInAndChannelAndCategory(Collection<Long> personIds, ChannelType channel, BroadcastCategory category);
}
