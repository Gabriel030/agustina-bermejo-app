package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentQuantifier;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCriterionRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentSpecRequest;
import com.red_escuela.backend.notification.broadcast.audience.specialist.SegmentResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SegmentResolverIT extends AbstractIntegrationTest {

    @Autowired
    private SegmentResolver segmentResolver;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    private static SegmentSpecRequest spec(SegmentQuantifier quantifier, String... roles) {
        return new SegmentSpecRequest(1, quantifier, java.util.Arrays.stream(roles)
                .map(role -> new SegmentCriterionRequest(SegmentKey.STAFF_ROLE, role)).toList());
    }

    @Test
    void staffRoleCriterionReturnsStaff() {
        Person teacher = AudienceTestPersons.create(entityManager, roleRepository, "seg-teacher", true, null, "DOCENTE");
        Person student = AudienceTestPersons.create(entityManager, roleRepository, "seg-student", true, null, "ALUMNO");

        Set<Long> result = segmentResolver.resolvePersonIds(spec(SegmentQuantifier.ALL, "DOCENTE"));

        assertThat(result).contains(teacher.getId()).doesNotContain(student.getId());
    }

    @Test
    void studentInTwoCriteriaAppearsOnce() {
        Person both = AudienceTestPersons.create(entityManager, roleRepository, "seg-both", true, null, "DOCENTE", "PRECEPTOR");

        Set<Long> result = segmentResolver.resolvePersonIds(spec(SegmentQuantifier.ANY, "DOCENTE", "PRECEPTOR"));

        assertThat(List.copyOf(result)).filteredOn(id -> id.equals(both.getId())).hasSize(1);
    }

    @Test
    void allQuantifierIntersectsCriteria() {
        Person both = AudienceTestPersons.create(entityManager, roleRepository, "seg-inter-both", true, null, "DOCENTE", "PRECEPTOR");
        Person onlyTeacher = AudienceTestPersons.create(entityManager, roleRepository, "seg-inter-teacher", true, null, "DOCENTE");

        Set<Long> result = segmentResolver.resolvePersonIds(spec(SegmentQuantifier.ALL, "DOCENTE", "PRECEPTOR"));

        assertThat(result).contains(both.getId()).doesNotContain(onlyTeacher.getId());
    }
}
