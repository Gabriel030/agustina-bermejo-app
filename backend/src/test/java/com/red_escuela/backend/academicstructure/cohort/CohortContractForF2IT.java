package com.red_escuela.backend.academicstructure.cohort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CohortContractForF2IT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PreceptorAssignmentRepository assignments;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PermissionSnapshotCache permissionCache;

    private Person administrative;
    private Program program;
    private ProgramCohort cohort;

    @BeforeEach
    void seed() {
        administrative = person("ADMINISTRATIVO");
        Address address = new Address();
        entityManager.persist(address);
        program = new Program();
        program.setName("Carrera F2 " + SEQUENCE.incrementAndGet() + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
        Term term = TermFixtures.newTerm();
        term.setYear(2030 + SEQUENCE.get() % 50);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
        template.setCreatedBy(administrative);
        entityManager.persist(template);
        TestTemplateTerms.publish(entityManager, template);
        cohort = new ProgramCohort();
        cohort.setTemplateId(template.getId());
        cohort.setProgramId(program.getId());
        cohort.setTermId(term.getId());
        cohort.setIntake(CohortIntake.SECOND);
        cohort.setAdmissionStart(LocalDate.now().minusDays(30));
        cohort.setAdmissionEnd(LocalDate.now().plusDays(30));
        cohort.setCursadaStart(LocalDate.now());
        cohort.setCreatedBy(administrative);
        entityManager.persist(cohort);
        entityManager.flush();
    }

    @AfterEach
    void invalidatePermissionCache() {
        permissionCache.invalidate();
    }

    @Test
    void cohortListingIncludesPlanIntakeOpenedAtAndResponsiblePreceptors() throws Exception {
        Person preceptor = person("PRECEPTOR");
        assign(preceptor);

        mockMvc.perform(get("/programs/" + program.getId() + "/cohorts").header("Authorization", bearer(administrative)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cohort.getId()))
                .andExpect(jsonPath("$[0].intake").value("SECOND"))
                .andExpect(jsonPath("$[0].planId").value(cohort.getPlan().getId()))
                .andExpect(jsonPath("$[0].planName").value(cohort.getPlan().getName()))
                .andExpect(jsonPath("$[0].openedAt").doesNotExist())
                .andExpect(jsonPath("$[0].responsiblePreceptors.length()").value(1))
                .andExpect(jsonPath("$[0].responsiblePreceptors[0].id").value(preceptor.getId()))
                .andExpect(jsonPath("$[0].responsiblePreceptors[0].name").value(preceptor.getName() + " " + preceptor.getLastname()));
    }

    @Test
    void openedCohortListingExposesOpenedAt() throws Exception {
        cohort.markOpened(LocalDateTime.of(2026, 5, 1, 10, 0));
        entityManager.flush();

        mockMvc.perform(get("/programs/" + program.getId() + "/cohorts").header("Authorization", bearer(administrative)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].openedAt").value("2026-05-01T10:00:00"))
                .andExpect(jsonPath("$[0].responsiblePreceptors.length()").value(0));
    }

    @Test
    void replacingTheResponsiblesKeepsTheNewSetRemovesTheOthersAndAuditsBoth() throws Exception {
        Person kept = person("PRECEPTOR");
        Person removed = person("PRECEPTOR");
        Person added = person("PRECEPTOR");
        assign(kept);
        assign(removed);
        int assignedBefore = auditCount("PRECEPTOR_ASSIGNED");
        int unassignedBefore = auditCount("PRECEPTOR_UNASSIGNED");

        mockMvc.perform(replace(administrative, kept.getId(), added.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(kept.getId()))
                .andExpect(jsonPath("$[1].id").value(added.getId()))
                .andExpect(jsonPath("$[1].name").value(added.getName() + " " + added.getLastname()));

        entityManager.flush();
        entityManager.clear();
        assertThat(assignments.findByProgramCohortId(cohort.getId())).extracting(PreceptorAssignment::getPersonId)
                .containsExactlyInAnyOrder(kept.getId(), added.getId());
        assertThat(auditCount("PRECEPTOR_ASSIGNED") - assignedBefore).isEqualTo(1);
        assertThat(auditCount("PRECEPTOR_UNASSIGNED") - unassignedBefore).isEqualTo(1);
    }

    @Test
    void replacingWithAPersonWithoutThePreceptorRoleReturnsConflictByCodeAndChangesNothing() throws Exception {
        Person kept = person("PRECEPTOR");
        assign(kept);

        mockMvc.perform(replace(administrative, kept.getId(), person("DOCENTE").getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRECEPTOR_ROLE_REQUIRED"));

        entityManager.clear();
        assertThat(assignments.findByProgramCohortId(cohort.getId())).extracting(PreceptorAssignment::getPersonId).containsExactly(kept.getId());
    }

    @Test
    void replacingWithAnEmptyListIsRejected() throws Exception {
        mockMvc.perform(put("/cohorts/" + cohort.getId() + "/responsible-preceptors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsiblePreceptorIds\":[]}").header("Authorization", bearer(administrative)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replacingTheResponsiblesOfAnUnknownCohortReturnsNotFound() throws Exception {
        mockMvc.perform(put("/cohorts/999999999/responsible-preceptors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsiblePreceptorIds\":[" + person("PRECEPTOR").getId() + "]}").header("Authorization", bearer(administrative)))
                .andExpect(status().isNotFound());
    }

    @Test
    void directorCanReplaceButTeacherAndStudentAreForbidden() throws Exception {
        Person preceptor = person("PRECEPTOR");

        mockMvc.perform(replace(person("DIRECTOR"), preceptor.getId())).andExpect(status().isOk());
        for (String role : new String[] {"DOCENTE", "ALUMNO"}) {
            mockMvc.perform(replace(person(role), preceptor.getId())).andExpect(status().isForbidden());
        }
    }

    @Test
    void removingThePermissionFromTheGridBlocksAdministrative() throws Exception {
        jdbc.update("insert into permission_module_mode (role, module, mode) values ('ADMINISTRATIVO', 'academicstructure', 'ENFORCE') on conflict (role, module) do update set mode = 'ENFORCE'");
        jdbc.update("delete from role_permission where role = 'ADMINISTRATIVO' and permission_id = (select id from permission where code = 'academicstructure.manage_preceptor_assignments')");
        permissionCache.invalidate();

        mockMvc.perform(replace(administrative, person("PRECEPTOR").getId())).andExpect(status().isForbidden());
    }

    @Test
    void directorAndAdministrativeCanListPreceptorsAndTeacherCannot() throws Exception {
        Person preceptor = person("PRECEPTOR");

        for (String role : new String[] {"DIRECTOR", "ADMINISTRATIVO"}) {
            mockMvc.perform(get("/staff").param("role", "PRECEPTOR").header("Authorization", bearer(person(role))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + preceptor.getId() + ")]").exists());
        }
        mockMvc.perform(get("/staff").param("role", "PRECEPTOR").header("Authorization", bearer(person("DOCENTE"))))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder replace(Person actor, Long... personIds) {
        String ids = String.join(",", java.util.Arrays.stream(personIds).map(String::valueOf).toList());
        return put("/cohorts/" + cohort.getId() + "/responsible-preceptors").contentType(MediaType.APPLICATION_JSON)
                .content("{\"responsiblePreceptorIds\":[" + ids + "]}").header("Authorization", bearer(actor));
    }

    private void assign(Person preceptor) {
        assignments.saveAndFlush(PreceptorAssignment.builder().personId(preceptor.getId()).programCohortId(cohort.getId())
                .createdBy(administrative.getId()).build());
        entityManager.clear();
        cohort = entityManager.find(ProgramCohort.class, cohort.getId());
    }

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_type = 'PRECEPTOR_ASSIGNMENT'", Integer.class, action);
    }

    private Person person(String roleName) {
        int n = SEQUENCE.incrementAndGet();
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Nombre" + n);
        person.setLastname("Apellido" + n);
        String dni = "8" + System.nanoTime() + n;
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
