package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.domain.ConsentStatus;
import com.red_escuela.backend.notification.broadcast.entity.ContactConsent;
import com.red_escuela.backend.notification.broadcast.repository.ContactConsentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContactConsentRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ContactConsentRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    private ContactConsent consent(Long personId, ChannelType channel, BroadcastCategory category) {
        return ContactConsent.builder().personId(personId).channel(channel).category(category)
                .status(ConsentStatus.GRANTED).source(ConsentSource.PROFILE).recordedAt(Instant.now(MutableClock.INSTANCE)).build();
    }

    @Test
    void uniquePerPersonChannelAndCategory() {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "consent-unique");
        repository.saveAndFlush(consent(person.getId(), ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL));
        repository.saveAndFlush(consent(person.getId(), ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL));
        repository.saveAndFlush(consent(person.getId(), ChannelType.WHATSAPP_LINK, BroadcastCategory.PROMOTIONAL));

        assertThatThrownBy(() -> repository.saveAndFlush(consent(person.getId(), ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsByPersonChannelAndCategoryAndListsByPerson() {
        Person person = BroadcastTestPersons.createStudentPerson(entityManager, roleRepository, "consent-find");
        repository.saveAndFlush(consent(person.getId(), ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL));

        assertThat(repository.findByPersonIdAndChannelAndCategory(person.getId(), ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL)).isPresent();
        assertThat(repository.findByPersonIdAndChannelAndCategory(person.getId(), ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL)).isEmpty();
        assertThat(repository.findAllByPersonId(person.getId())).hasSize(1);
    }
}
