package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;
import com.red_escuela.backend.attendance.dto.RevertAttendanceChangeRequestDecisionRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttendanceChangeRequestScopeCharacterizationTest extends CourseworkCharacterizationSupport {

    private static final String VALID_REASON = "La decisión se tomó con datos incompletos";

    private Classroom classroom(String key, String dniBase) {
        return persistClassroom(key, dniBase, persistScheme(2, true, null),
                Percentage.ofPoints(new BigDecimal("25")), 10, 10, InstructorRole.LEAD);
    }

    private long pendingRequestOf(Classroom classroom) throws Exception {
        var load = new CargarAsistenciaRequest(LocalDate.of(2026, 3, 2),
                List.of(new AttendanceEntry(classroom.enrollment().getId(), AttendanceStatus.ABSENT)), null, null);
        long attendanceId = bodyOf(postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance", load)
                .andExpect(status().isCreated())).get(0).get("id").asLong();
        return bodyOf(postJson(classroom.teacher(), "/attendance/" + attendanceId + "/change-requests",
                new CreateAttendanceChangeRequestRequest(AttendanceStatus.PRESENT, "El alumno estaba presente"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private void assignToSection(Person preceptor, Classroom classroom) {
        jdbcTemplate.update("insert into preceptor_assignment (person_id, subject_section_id, created_by) values (?, ?, ?)",
                preceptor.getId(), classroom.section().getId(), preceptor.getId());
    }

    private Long enrollInNewCohort(Classroom classroom, Person createdBy) {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(classroom.programEnrollment().getProgram());
        template.setTerm(term);
        TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
        template.setCreatedBy(createdBy);
        entityManager.persist(template);
        TestTemplateTerms.publish(entityManager, template);
        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplateId(template.getId());
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(LocalDate.now().minusDays(30));
        cohort.setAdmissionEnd(LocalDate.now().plusDays(30));
        cohort.setCursadaStart(LocalDate.now());
        cohort.setCreatedBy(createdBy);
        entityManager.persist(cohort);
        classroom.programEnrollment().setProgramCohort(cohort);
        entityManager.flush();
        return cohort.getId();
    }

    private void assignToCohort(Person preceptor, Long cohortId) {
        jdbcTemplate.update("insert into preceptor_assignment (person_id, program_cohort_id, created_by) values (?, ?, ?)",
                preceptor.getId(), cohortId, preceptor.getId());
    }

    @Test
    void aPreceptorWithoutAssignmentsSeesAnEmptyQueueAndAnEmptyHistory() throws Exception {
        long requestId = pendingRequestOf(classroom("scope1", "7800001"));
        Person preceptor = persistPerson("scope1.preceptor", "78000017", "PRECEPTOR");

        getAs(preceptor, "/attendance/change-requests?status=PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + requestId + ")]").isEmpty());
        getAs(preceptor, "/attendance/change-requests/resolved")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aPreceptorSeesOnlyTheRequestsOfTheAssignedSection() throws Exception {
        Classroom own = classroom("scope2a", "7800002");
        Classroom foreign = classroom("scope2b", "7800003");
        long ownRequest = pendingRequestOf(own);
        long foreignRequest = pendingRequestOf(foreign);
        Person preceptor = persistPerson("scope2.preceptor", "78000027", "PRECEPTOR");
        assignToSection(preceptor, own);

        getAs(preceptor, "/attendance/change-requests?status=PENDING")
                .andExpect(jsonPath("$[?(@.id==" + ownRequest + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id==" + foreignRequest + ")]").isEmpty());
    }

    @Test
    void aPreceptorCannotDecideOrRevertARequestOutsideTheAssignedScope() throws Exception {
        Classroom own = classroom("scope3a", "7800004");
        Classroom foreign = classroom("scope3b", "7800005");
        pendingRequestOf(own);
        long foreignRequest = pendingRequestOf(foreign);
        Person preceptor = persistPerson("scope3.preceptor", "78000037", "PRECEPTOR");
        assignToSection(preceptor, own);

        postEmpty(preceptor, "/attendance/change-requests/" + foreignRequest + "/approve").andExpect(status().isNotFound());
        postJson(preceptor, "/attendance/change-requests/" + foreignRequest + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest(VALID_REASON)).andExpect(status().isNotFound());
    }

    @Test
    void aPreceptorDecidesARequestOfTheAssignedSectionAndSeesItInTheHistory() throws Exception {
        Classroom own = classroom("scope4", "7800006");
        long requestId = pendingRequestOf(own);
        Person preceptor = persistPerson("scope4.preceptor", "78000047", "PRECEPTOR");
        assignToSection(preceptor, own);

        postEmpty(preceptor, "/attendance/change-requests/" + requestId + "/approve").andExpect(status().isOk());

        getAs(preceptor, "/attendance/change-requests/resolved?result=APPROVED")
                .andExpect(jsonPath("$.items[?(@.id==" + requestId + ")]").isNotEmpty());
    }

    @Test
    void aPreceptorAssignedToACohortSeesOnlyTheRequestsOfStudentsOfThatCohort() throws Exception {
        Classroom inCohort = classroom("scope7a", "7800010");
        Classroom outsideCohort = classroom("scope7b", "7800011");
        long cohortRequest = pendingRequestOf(inCohort);
        long outsideRequest = pendingRequestOf(outsideCohort);
        Person director = persistPerson("scope7.director", "78000107", "DIRECTOR");
        Long cohortId = enrollInNewCohort(inCohort, director);
        Person preceptor = persistPerson("scope7.preceptor", "78000117", "PRECEPTOR");
        assignToCohort(preceptor, cohortId);

        getAs(preceptor, "/attendance/change-requests?status=PENDING")
                .andExpect(jsonPath("$[?(@.id==" + cohortRequest + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id==" + outsideRequest + ")]").isEmpty());
        postEmpty(preceptor, "/attendance/change-requests/" + outsideRequest + "/approve").andExpect(status().isNotFound());
    }

    @Test
    void directorAdministrativeAndDeputyDirectorSeeTheWholeQueueAndTheHistory() throws Exception {
        long requestId = pendingRequestOf(classroom("scope5", "7800007"));
        for (String role : List.of("DIRECTOR", "ADMINISTRATIVO", "SUBDIRECTOR")) {
            Person actor = persistPerson("scope5." + role.toLowerCase(), "7800008" + role.length(), role);
            getAs(actor, "/attendance/change-requests?status=PENDING")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id==" + requestId + ")]").isNotEmpty());
            getAs(actor, "/attendance/change-requests/resolved").andExpect(status().isOk());
        }
    }

    @Test
    void theDirectorApprovesAndRevertsARequestOfAnyProgram() throws Exception {
        long requestId = pendingRequestOf(classroom("scope6", "7800009"));
        Person director = persistPerson("scope6.director", "78000097", "DIRECTOR");

        postEmpty(director, "/attendance/change-requests/" + requestId + "/approve").andExpect(status().isOk());
        postJson(director, "/attendance/change-requests/" + requestId + "/revert",
                new RevertAttendanceChangeRequestDecisionRequest(VALID_REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
