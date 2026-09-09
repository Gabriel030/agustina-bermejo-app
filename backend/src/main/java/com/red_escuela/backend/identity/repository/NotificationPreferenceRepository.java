package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.identity.entity.NotificationPreference;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByPerson(Person person);

    Optional<NotificationPreference> findByPersonAndEventAndChannel(
            Person person, NotificationEvent event, NotificationChannel channel);

    void deleteByPerson(Person person);
}
