package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.audience.domain.AudienceRecipient;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.audience.specialist.RecipientEligibilityEvaluator;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecipientEligibilityQueryCountIT extends AbstractIntegrationTest {

    private static final int FEW = 50;
    private static final int MANY = 500;
    private static final int CONSENT_AND_NEWSLETTER_QUERIES = 2;

    @Autowired
    private RecipientEligibilityEvaluator evaluator;

    @Autowired
    private ContactConsentLedger ledger;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private RoleRepository roleRepository;

    private int sequence;

    private List<AudienceRecipient> audienceOf(int size) {
        List<AudienceRecipient> recipients = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Person person = AudienceTestPersons.create(entityManager, roleRepository, "elig-count-" + (sequence++), true, null);
            if (i % 2 == 0) {
                ledger.recordConsent(person.getId(), ConsentChannel.EMAIL, BroadcastCategory.PROMOTIONAL, i % 4 == 0, ConsentSource.PROFILE, null);
            }
            recipients.add(new AudienceRecipient(person.getId(), person.getName(), person.getEmail().value(), true, null));
        }
        entityManager.flush();
        entityManager.clear();
        return recipients;
    }

    private Map<Long, ExclusionReason> evaluateCounting(List<AudienceRecipient> recipients) {
        return QueryCountAssertions.assertAtMostQueries(entityManagerFactory, CONSENT_AND_NEWSLETTER_QUERIES,
                () -> evaluator.findExclusionReasons(recipients, ChannelType.EMAIL, BroadcastCategory.PROMOTIONAL));
    }

    @Test
    void consentQueriesDoNotGrowWithAudienceSize() {
        List<AudienceRecipient> few = audienceOf(FEW);
        List<AudienceRecipient> many = audienceOf(MANY);

        Map<Long, ExclusionReason> fewReasons = evaluateCounting(few);
        Map<Long, ExclusionReason> manyReasons = evaluateCounting(many);

        assertThat(fewReasons).hasSize(FEW - FEW / 4 - 1);
        assertThat(manyReasons).hasSize(MANY - MANY / 4);
    }
}
