package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InvalidRegistrationWindowException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseServiceIT extends AbstractIntegrationTest {

    private static final long MISSING_ID = 987_654_321L;
    private static final LocalDateTime OPENING = LocalDateTime.of(2040, 2, 1, 9, 0);
    private static final LocalDateTime CLOSING = LocalDateTime.of(2040, 3, 1, 9, 0);

    @Autowired
    private CourseService courseService;
    @Autowired
    private EntityManager entityManager;

    private Term term;

    @BeforeEach
    void authenticateAStaffMemberAndSeedATerm() {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Person staff = fixtures.person();
        term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staff.getId().toString(), null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private static Money usd(String amount) {
        return Money.of(new BigDecimal(amount), Currency.USD);
    }

    private CreateCourseRequest request(String name, Money price, Currency currency, LocalDateTime start, LocalDateTime end) {
        return new CreateCourseRequest(name, term.getId(), "Lunes 18hs", 30, price, 6, TeachingMode.ONLINE, start, end, currency);
    }

    private Course createCourse(String name) {
        return courseService.create(request(name, ars("1000"), null, OPENING, CLOSING));
    }

    @Test
    void aCourseWithoutPriceOrCurrencyDefaultsToPesosAndStartsActive() {
        Course course = courseService.create(request("Sin precio", null, null, null, null));

        assertThat(course.getCurrency()).isEqualTo(Currency.ARS);
        assertThat(course.getIsActive()).isEqualTo(StatusType.ACTIVE);
        assertThat(course.priceAsMoney()).isNull();
    }

    @Test
    void theRequestedCurrencyIsUsedWhenTheCourseHasNoPrice() {
        Course course = courseService.create(request("Moneda pedida", null, Currency.USD, null, null));

        assertThat(course.getCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void theCourseCurrencyFollowsTheCurrencyOfItsPrice() {
        Course course = courseService.create(request("En dolares", usd("20"), null, null, null));

        assertThat(course.getCurrency()).isEqualTo(Currency.USD);
        assertThat(course.priceAsMoney()).isEqualTo(usd("20"));
    }

    @Test
    void aRequestedCurrencyEqualToThePriceCurrencyIsAccepted() {
        Course course = courseService.create(request("Moneda coincidente", usd("20"), Currency.USD, null, null));

        assertThat(course.getCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void aRequestedCurrencyDifferentFromThePriceCurrencyIsRejected() {
        assertThatThrownBy(() -> courseService.create(request("Moneda cruzada", ars("100"), Currency.USD, null, null)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void creatingACourseInAMissingTermFailsWithNotFound() {
        CreateCourseRequest request = new CreateCourseRequest("Sin ciclo", MISSING_ID, null, 10, null, 3, null, null, null, null);

        assertThatThrownBy(() -> courseService.create(request)).isInstanceOf(TermNotFoundException.class);
    }

    @Test
    void aRegistrationWindowThatClosesBeforeItOpensIsRejectedOnCreate() {
        assertThatThrownBy(() -> courseService.create(request("Ventana invertida", null, null, CLOSING, OPENING)))
                .isInstanceOf(InvalidRegistrationWindowException.class);
    }

    @Test
    void updatingOnlyTheNameKeepsEveryOtherField() {
        Course course = createCourse("Nombre viejo");

        Course updated = courseService.update(course.getId(),
                new UpdateCourseRequest("Nombre nuevo", null, null, null, null, null, null, null));

        assertThat(updated.getName()).isEqualTo("Nombre nuevo");
        assertThat(updated.getSchedule()).isEqualTo("Lunes 18hs");
        assertThat(updated.getCapacity()).isEqualTo(30);
        assertThat(updated.priceAsMoney()).isEqualTo(ars("1000"));
        assertThat(updated.getDurationMonths()).isEqualTo(6);
        assertThat(updated.getTeachingMode()).isEqualTo(TeachingMode.ONLINE);
        assertThat(updated.getRegistrationStart()).isEqualTo(OPENING);
        assertThat(updated.getRegistrationEnd()).isEqualTo(CLOSING);
    }

    @Test
    void updatingEveryFieldStoresTheNewValues() {
        Course course = createCourse("Completo");
        LocalDateTime newStart = OPENING.plusDays(1);
        LocalDateTime newEnd = CLOSING.plusDays(1);

        Course updated = courseService.update(course.getId(),
                new UpdateCourseRequest(null, "Martes 20hs", 45, ars("1500"), 9, TeachingMode.HYBRID, newStart, newEnd));

        assertThat(updated.getSchedule()).isEqualTo("Martes 20hs");
        assertThat(updated.getCapacity()).isEqualTo(45);
        assertThat(updated.priceAsMoney()).isEqualTo(ars("1500"));
        assertThat(updated.getDurationMonths()).isEqualTo(9);
        assertThat(updated.getTeachingMode()).isEqualTo(TeachingMode.HYBRID);
        assertThat(updated.getRegistrationStart()).isEqualTo(newStart);
        assertThat(updated.getRegistrationEnd()).isEqualTo(newEnd);
    }

    @Test
    void updatingWithTheSamePriceKeepsThePriceAndDoesNotFail() {
        Course course = createCourse("Mismo precio");

        Course updated = courseService.update(course.getId(),
                new UpdateCourseRequest(null, null, null, ars("1000"), null, null, null, null));

        assertThat(updated.priceAsMoney()).isEqualTo(ars("1000"));
    }

    @Test
    void updatingThePriceInAnotherCurrencyIsRejectedAndKeepsTheOldPrice() {
        Course course = createCourse("Precio cruzado");
        UpdateCourseRequest request = new UpdateCourseRequest(null, null, null, usd("10"), null, null, null, null);

        assertThatThrownBy(() -> courseService.update(course.getId(), request)).isInstanceOf(CurrencyMismatchException.class);

        assertThat(course.priceAsMoney()).isEqualTo(ars("1000"));
    }

    @Test
    void updatingToAnInvertedRegistrationWindowIsRejected() {
        Course course = createCourse("Ventana");
        UpdateCourseRequest request = new UpdateCourseRequest(null, null, null, null, null, null, CLOSING.plusDays(5), null);

        assertThatThrownBy(() -> courseService.update(course.getId(), request))
                .isInstanceOf(InvalidRegistrationWindowException.class);
    }

    @Test
    void updatingAMissingCourseFailsWithNotFound() {
        UpdateCourseRequest request = new UpdateCourseRequest("X", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> courseService.update(MISSING_ID, request)).isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void gettingAMissingCourseFailsWithNotFound() {
        assertThatThrownBy(() -> courseService.get(MISSING_ID)).isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void deactivatingAndReactivatingTogglesTheStatus() {
        Course course = createCourse("Estado");

        courseService.deactivate(course.getId());
        assertThat(courseService.get(course.getId()).getIsActive()).isEqualTo(StatusType.INACTIVE);

        courseService.reactivate(course.getId());
        assertThat(courseService.get(course.getId()).getIsActive()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void deactivatingOrReactivatingAMissingCourseFailsWithNotFound() {
        assertThatThrownBy(() -> courseService.deactivate(MISSING_ID)).isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> courseService.reactivate(MISSING_ID)).isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void listFiltersByStatusAndCatalogVisibility() {
        Course active = createCourse("Zeta activo visible");
        active.setVisibleInCatalog(true);
        Course hidden = createCourse("Zeta activo oculto");
        Course inactive = createCourse("Zeta inactivo");
        courseService.deactivate(inactive.getId());
        entityManager.flush();

        List<Long> all = ids(courseService.list(null, null));
        List<Long> onlyActive = ids(courseService.list(StatusType.ACTIVE, null));
        List<Long> onlyInactive = ids(courseService.list(StatusType.INACTIVE, null));
        List<Long> visible = ids(courseService.list(null, true));
        List<Long> notVisible = ids(courseService.list(null, false));

        assertThat(all).contains(active.getId(), hidden.getId(), inactive.getId());
        assertThat(onlyActive).contains(active.getId(), hidden.getId()).doesNotContain(inactive.getId());
        assertThat(onlyInactive).contains(inactive.getId()).doesNotContain(active.getId(), hidden.getId());
        assertThat(visible).contains(active.getId()).doesNotContain(hidden.getId(), inactive.getId());
        assertThat(notVisible).contains(hidden.getId(), inactive.getId()).doesNotContain(active.getId());
    }

    @Test
    void listIsSortedByNameThenId() {
        Course second = createCourse("Orden 2");
        Course first = createCourse("Orden 1");

        List<Long> sorted = ids(courseService.list(null, null));

        assertThat(sorted.indexOf(first.getId())).isLessThan(sorted.indexOf(second.getId()));
    }

    private static List<Long> ids(List<Course> courses) {
        return courses.stream().map(Course::getId).toList();
    }
}
