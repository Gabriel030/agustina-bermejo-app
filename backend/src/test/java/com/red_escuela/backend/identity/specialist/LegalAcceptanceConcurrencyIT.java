package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.ConcurrentRace;
import com.red_escuela.backend.config.ConcurrentRace.Outcome;
import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LegalAcceptanceConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private LegalAcceptanceRecorder legalAcceptanceRecorder;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long personId;

    @AfterEach
    void cleanUp() {
        if (personId != null) {
            jdbcTemplate.update("delete from legal_acceptance where person_id = ?", personId);
            jdbcTemplate.update("delete from person where id = ?", personId);
        }
    }

    @Test
    void simultaneousAcceptancesLeaveExactlyOneRowPerDocumentWithoutFailures() throws Exception {
        personId = transactionTemplate.execute(status -> {
            Person person = new Person();
            person.setUsername("legal-race-" + System.nanoTime());
            person.setEmail(person.getUsername() + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            return personRepository.save(person).getId();
        });
        List<Callable<Boolean>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(() -> {
                transactionTemplate.executeWithoutResult(status ->
                        legalAcceptanceRecorder.acceptCurrentDocuments(personId, LegalAcceptanceEvidence.none()));
                return true;
            });
        }

        List<Outcome<Boolean>> outcomes = ConcurrentRace.run(calls);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(jdbcTemplate.queryForObject("select count(*) from legal_acceptance where person_id = ?",
                Integer.class, personId)).isEqualTo(2);
        assertThat(legalAcceptanceRecorder.pendingDocuments(personId)).isEmpty();
    }
}
