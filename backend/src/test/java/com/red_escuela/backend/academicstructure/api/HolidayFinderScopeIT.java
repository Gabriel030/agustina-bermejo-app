package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.HolidayScope;
import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HolidayFinderScopeIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private HolidayFinder holidayFinder;

    @Test
    void findBetweenReturnsOnlyHolidaysThatAffectClasses() {
        holiday(LocalDate.of(2160, 4, 1), "Solo clases", HolidayScope.CLASSES);
        holiday(LocalDate.of(2160, 4, 2), "Solo cobranza", HolidayScope.BILLING);
        holiday(LocalDate.of(2160, 4, 3), "Ambos", HolidayScope.BOTH);
        entityManager.flush();
        entityManager.clear();

        assertThat(holidayFinder.findBetween(LocalDate.of(2160, 4, 1), LocalDate.of(2160, 4, 30)))
                .extracting(HolidayView::name).containsExactly("Solo clases", "Ambos");
    }

    private void holiday(LocalDate date, String name, HolidayScope scope) {
        Holiday holiday = new Holiday();
        holiday.setDate(date);
        holiday.setName(name);
        holiday.setType(HolidayType.NATIONAL);
        holiday.setScope(scope);
        entityManager.persist(holiday);
    }
}
