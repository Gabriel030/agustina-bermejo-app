package com.red_escuela.backend.characterization.academicorg;

import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseWithPriceCharacterizationTest extends AcademicOrgCharacterizationSupport {

    private CreateCourseRequest courseRequest(String name, Long termId, Money price) {
        return new CreateCourseRequest(name, termId, "Martes 19hs", 25, price, 6, null, null, null);
    }

    @Test
    void administrativeCreatesCourseWithPriceAndTheInitialPriceIsServedThroughThePricingEndpoint() throws Exception {
        Person admin = persistPerson("course.admin1", "74000001", "ADMINISTRATIVO");
        Term term = persistTerm();
        Money price = Money.of(new BigDecimal("15000.50"), Currency.ARS);

        long courseId = idOf(postJson(admin, "/courses", courseRequest("Curso de Excel", term.getId(), price))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Curso de Excel"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.capacity").value(25))
                .andExpect(jsonPath("$.durationMonths").value(6))
                .andExpect(jsonPath("$.price.amount").value(15000.50))
                .andExpect(jsonPath("$.price.currency").value("ARS")));

        getAs(admin, "/courses/" + courseId + "/pricing")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.cuotaMensual.amount").value(15000.50))
                .andExpect(jsonPath("$.cuotaMensual.currency").value("ARS"));
    }

    @Test
    void changingTheCoursePriceIsReflectedByTheDetailAndPricingEndpoints() throws Exception {
        Person admin = persistPerson("course.admin2", "74000002", "ADMINISTRATIVO");
        Term term = persistTerm();
        long courseId = idOf(postJson(admin, "/courses",
                courseRequest("Curso de Word", term.getId(), Money.of(new BigDecimal("10000"), Currency.ARS)))
                .andExpect(status().isCreated()));

        putJson(admin, "/courses/" + courseId,
                new UpdateCourseRequest(null, null, null, Money.of(new BigDecimal("12500.00"), Currency.ARS), null, null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(12500.00));

        getAs(admin, "/courses/" + courseId).andExpect(jsonPath("$.price.amount").value(12500.00));
        getAs(admin, "/courses/" + courseId + "/pricing").andExpect(jsonPath("$.cuotaMensual.amount").value(12500.00));
    }

    @Test
    void updatingACourseWithoutPriceKeepsTheCurrentPrice() throws Exception {
        Person admin = persistPerson("course.admin3", "74000003", "ADMINISTRATIVO");
        Term term = persistTerm();
        long courseId = idOf(postJson(admin, "/courses",
                courseRequest("Curso de Access", term.getId(), Money.of(new BigDecimal("8000"), Currency.ARS)))
                .andExpect(status().isCreated()));

        putJson(admin, "/courses/" + courseId, new UpdateCourseRequest("Curso de Access 2", null, null, null, null, null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Curso de Access 2"))
                .andExpect(jsonPath("$.price.amount").value(8000.00));
    }

    @Test
    void courseWithoutPriceIsCreatedWithNullPrice() throws Exception {
        Person admin = persistPerson("course.admin4", "74000004", "ADMINISTRATIVO");
        Term term = persistTerm();

        long courseId = idOf(postJson(admin, "/courses", courseRequest("Curso gratuito", term.getId(), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").doesNotExist()));

        getAs(admin, "/courses/" + courseId + "/pricing").andExpect(status().isOk());
    }

    @Test
    void negativePriceAndUnknownTermAndNonAdministrativeActorsAreRejected() throws Exception {
        Person admin = persistPerson("course.admin5", "74000005", "ADMINISTRATIVO");
        Person teacher = persistPerson("course.teacher5", "74000006", "DOCENTE");
        Term term = persistTerm();

        postJson(admin, "/courses", courseRequest("Negativo", term.getId(), Money.of(new BigDecimal("-1"), Currency.ARS)))
                .andExpect(status().isBadRequest());
        postJson(admin, "/courses", courseRequest("Sin periodo", 999999L, Money.of(new BigDecimal("100"), Currency.ARS)))
                .andExpect(status().isNotFound());
        postJson(teacher, "/courses", courseRequest("Docente", term.getId(), Money.of(new BigDecimal("100"), Currency.ARS)))
                .andExpect(status().isForbidden());
    }
}
