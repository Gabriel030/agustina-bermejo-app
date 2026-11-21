package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.HolidayType;
import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HolidayAndTermPeriodReadIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private HolidayFinder holidayFinder;
    @Autowired
    private TermPeriodFinder termPeriodFinder;

    @Test
    void findBetweenIncludesBothBoundaryDatesAndExcludesOutsideOnes() {
        holiday(LocalDate.of(2150, 5, 24), "Antes", HolidayType.NATIONAL);
        holiday(LocalDate.of(2150, 5, 25), "Primer día", HolidayType.NATIONAL);
        holiday(LocalDate.of(2150, 6, 20), "Último día", HolidayType.PROVINCIAL);
        holiday(LocalDate.of(2150, 6, 21), "Después", HolidayType.BRIDGE);
        entityManager.flush();
        entityManager.clear();

        List<HolidayView> views = holidayFinder.findBetween(LocalDate.of(2150, 5, 25), LocalDate.of(2150, 6, 20));

        assertThat(views).containsExactly(
                new HolidayView(LocalDate.of(2150, 5, 25), "Primer día", "NATIONAL"),
                new HolidayView(LocalDate.of(2150, 6, 20), "Último día", "PROVINCIAL"));
    }

    @Test
    void findBetweenReturnsHolidaysSortedByDate() {
        holiday(LocalDate.of(2151, 7, 9), "Tarde", HolidayType.NATIONAL);
        holiday(LocalDate.of(2151, 7, 1), "Temprano", HolidayType.OTHER);
        entityManager.flush();

        assertThat(holidayFinder.findBetween(LocalDate.of(2151, 7, 1), LocalDate.of(2151, 7, 31)))
                .extracting(HolidayView::name).containsExactly("Temprano", "Tarde");
    }

    @Test
    void findBetweenWithoutHolidaysIsEmpty() {
        assertThat(holidayFinder.findBetween(LocalDate.of(2152, 1, 1), LocalDate.of(2152, 1, 31))).isEmpty();
    }

    @Test
    void findPeriodsReturnsEveryRequestedTermInOneCall() {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Term first = fixtures.term(2153);
        Term second = fixtures.term(2154);
        entityManager.flush();
        entityManager.clear();

        Map<Long, TermPeriodView> periods = termPeriodFinder.findPeriods(List.of(first.getId(), second.getId()));

        assertThat(periods).containsOnlyKeys(first.getId(), second.getId());
        assertThat(periods.get(first.getId())).isEqualTo(termPeriodFinder.findPeriod(first.getId()));
        assertThat(periods.get(second.getId()).startDate().toLocalDate()).isEqualTo(LocalDate.of(2154, 3, 1));
        assertThat(termPeriodFinder.findPeriods(List.of())).isEmpty();
    }

    private void holiday(LocalDate date, String name, HolidayType type) {
        Holiday holiday = new Holiday();
        holiday.setDate(date);
        holiday.setName(name);
        holiday.setType(type);
        entityManager.persist(holiday);
    }
}
