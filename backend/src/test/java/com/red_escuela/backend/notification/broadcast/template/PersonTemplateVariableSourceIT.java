package com.red_escuela.backend.notification.broadcast.template;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;
import com.red_escuela.backend.notification.broadcast.template.specialist.TemplateVariableSource;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PersonTemplateVariableSourceIT extends AbstractIntegrationTest {

    private static final int PEOPLE = 30;
    private static final int MAX_QUERIES_FOR_ANY_BATCH_SIZE = 3;

    @Autowired
    private TemplateVariableSource variableSource;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private List<Long> persistPeople(int count) {
        return transactionTemplate.execute(status -> {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String dni = "7" + System.nanoTime() + i;
                Address address = new Address();
                entityManager.persist(address);
                Person person = new Person();
                person.setName("Nombre" + i);
                person.setLastname("Apellido" + i);
                person.setDni(dni);
                person.setUsername(dni);
                person.setEmail(dni + "@example.com");
                person.setAddress(address);
                person.setStatus(StatusType.ACTIVE);
                entityManager.persist(person);
                ids.add(person.getId());
            }
            return ids;
        });
    }

    @Test
    void batchLookupRunsAFixedNumberOfQueriesWhateverTheRecipientCount() {
        List<Long> ids = persistPeople(PEOPLE);

        Map<Long, Map<TemplateVariable, String>> values = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                MAX_QUERIES_FOR_ANY_BATCH_SIZE, () -> variableSource.valuesForAll(ids));

        assertThat(values).hasSize(PEOPLE);
        values.forEach((personId, personValues) -> assertThat(personValues.get(TemplateVariable.FIRST_NAME)).startsWith("Nombre"));
    }

    @Test
    void batchValuesMatchThePerPersonLookup() {
        List<Long> ids = persistPeople(3);

        Map<Long, Map<TemplateVariable, String>> values = variableSource.valuesForAll(ids);

        ids.forEach(personId -> assertThat(values.get(personId)).isEqualTo(variableSource.valuesFor(personId)));
    }

    @Test
    void batchLookupIgnoresUnknownPeopleAndEmptyInput() {
        List<Long> ids = persistPeople(1);

        assertThat(variableSource.valuesForAll(List.of(ids.get(0), -1L))).containsOnlyKeys(ids.get(0));
        assertThat(variableSource.valuesForAll(List.of())).isEmpty();
    }

    @Test
    void courseVariablesAreLoadedInBatchWithoutExtraQueriesPerPerson() {
        List<Long> ids = persistPeople(PEOPLE);
        transactionTemplate.executeWithoutResult(status -> {
            PersonEnrollmentTestFixtures enrollments = new PersonEnrollmentTestFixtures(entityManager);
            for (Long id : ids) {
                var student = enrollments.studentOf(entityManager.find(Person.class, id));
                enrollments.enroll(student, "Carrera " + id, "Materia " + id, "Comisión " + id,
                        java.time.LocalDateTime.of(2041, 3, 1, 10, 0), com.red_escuela.backend.enrollment.entity.EnrollmentStatus.IN_PROGRESS);
            }
        });

        Map<Long, Map<TemplateVariable, String>> values = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                MAX_QUERIES_FOR_ANY_BATCH_SIZE, () -> variableSource.valuesForAll(ids));

        ids.forEach(id -> {
            assertThat(values.get(id).get(TemplateVariable.PROGRAM)).isEqualTo("Carrera " + id);
            assertThat(values.get(id).get(TemplateVariable.SUBJECT)).isEqualTo("Materia " + id);
            assertThat(values.get(id).get(TemplateVariable.SECTION)).isEqualTo("Comisión " + id);
        });
    }

    @Test
    void peopleWithoutEnrollmentGetNoCourseValues() {
        List<Long> ids = persistPeople(1);

        Map<TemplateVariable, String> values = variableSource.valuesFor(ids.get(0));

        assertThat(values).doesNotContainKeys(TemplateVariable.PROGRAM, TemplateVariable.SUBJECT, TemplateVariable.SECTION);
    }
}
