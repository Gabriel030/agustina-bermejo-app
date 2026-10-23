package com.red_escuela.backend.billing.collections.controller;

import com.red_escuela.backend.billing.collections.service.AbstractOverdueContactIT;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-339")
class OverdueContactListControllerIT extends AbstractOverdueContactIT {
    private static final String PATH = "/collection-reminders/contact-list";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    private String tokenWithRole(String roleName) {
        Person person = newPerson(roleName.toLowerCase(), roleName);
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private void today(LocalDate date) {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(date, java.time.LocalTime.of(10, 0)));
    }

    private String itemOf(Student student, String field) {
        return "$.items[?(@.studentId==" + student.getId() + ")]." + field;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRATIVO", "DIRECTOR"})
    @Tag("D-110")
    void administrativeStaffAndDirectorCanReadTheContactList(String role) throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", tokenWithRole(role))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRECEPTOR", "DOCENTE", "ALUMNO"})
    @Tag("D-110")
    void otherRolesCannotReadTheContactList(String role) throws Exception {
        Student overdue = newStudent();
        installmentDueOn(newCourseEnrollment(overdue), MARCH_ELEVENTH);
        today(MARCH_TWENTY_FIFTH);

        mockMvc.perform(get(PATH).header("Authorization", tokenWithRole(role)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString(overdue.getPerson().getDni()))));
    }

    @Test
    @Tag("D-110")
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-097")
    void theListShowsAgeInstallmentsAmountsPerCurrencyAndTheLastReminder() throws Exception {
        Student student = newStudent();
        installmentDueOn(newCourseEnrollment(student), MARCH_ELEVENTH, ars(100000));
        installmentDueOn(newCourseEnrollment(student), MARCH_ELEVENTH, usd(100));
        jdbc.update("insert into collection_reminder (student_id, business_date, in_app_messages, email_messages, suppressed, created_at) "
                + "values (?, '2027-03-12', '[{\"k\":1}]'::jsonb, '[]'::jsonb, '[]'::jsonb, now())", student.getId());
        jdbc.update("insert into collection_reminder (student_id, business_date, in_app_messages, email_messages, suppressed, created_at) "
                + "values (?, '2027-03-20', '[]'::jsonb, '[]'::jsonb, '[{\"k\":1}]'::jsonb, now())", student.getId());
        today(MARCH_TWENTY_FIFTH);

        mockMvc.perform(get(PATH + "?size=100").header("Authorization", tokenWithRole("ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(itemOf(student, "oldestDaysOverdue")).value(contains(14)))
                .andExpect(jsonPath(itemOf(student, "overdueInstallments")).value(contains(2)))
                .andExpect(jsonPath(itemOf(student, "overdueAmounts[*].currency")).value(contains("ARS", "USD")))
                .andExpect(jsonPath(itemOf(student, "lastReminderDate")).value(contains("2027-03-12")))
                .andExpect(jsonPath(itemOf(student, "dni")).value(contains(student.getPerson().getDni())));
    }

    @Test
    void theCurrencyFilterKeepsOnlyStudentsOwingThatCurrency() throws Exception {
        Student pesosOnly = newStudent();
        installmentDueOn(newCourseEnrollment(pesosOnly), MARCH_ELEVENTH, ars(50000));
        Student dollars = newStudent();
        installmentDueOn(newCourseEnrollment(dollars), MARCH_ELEVENTH, usd(80));
        today(MARCH_TWENTY_FIFTH);

        mockMvc.perform(get(PATH + "?size=100&currency=USD").header("Authorization", tokenWithRole("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(itemOf(dollars, "studentId")).isNotEmpty())
                .andExpect(jsonPath(itemOf(pesosOnly, "studentId")).isEmpty());
        mockMvc.perform(get(PATH + "?currency=EUR").header("Authorization", tokenWithRole("DIRECTOR"))).andExpect(status().isBadRequest());
    }

    @Test
    void thePageSizeIsCapped() throws Exception {
        Student first = newStudent();
        installmentDueOn(newCourseEnrollment(first), MARCH_ELEVENTH);
        Student second = newStudent();
        installmentDueOn(newCourseEnrollment(second), MARCH_ELEVENTH);
        today(MARCH_TWENTY_FIFTH);

        mockMvc.perform(get(PATH + "?page=0&size=1").header("Authorization", tokenWithRole("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.size").value(1));
        mockMvc.perform(get(PATH + "?page=-3&size=100000").header("Authorization", tokenWithRole("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100));
    }
}
