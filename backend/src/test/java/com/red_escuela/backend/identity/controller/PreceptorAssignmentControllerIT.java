package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PreceptorAssignmentControllerIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PreceptorAssignmentRepository assignments;
    @Autowired private JdbcTemplate jdbc;

    private CycleTestFixtures fixtures;
    private Term term;
    private SubjectSection programSection;
    private CourseSubjectSection courseSection;
    private Person administrative;

    @BeforeEach
    void seed() {
        fixtures = new CycleTestFixtures(entityManager);
        term = fixtures.term(2060 + (int) (System.nanoTime() % 30));
        programSection = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        courseSection = persistCourseSection();
        administrative = person("ADMINISTRATIVO");
    }

    @Test
    void administrativeAssignsAPreceptorToAProgramSectionAndTheAssignmentIsAudited() throws Exception {
        Person preceptor = person("PRECEPTOR");

        mockMvc.perform(assign(administrative, preceptor.getId(), programSection.getId(), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personId").value(preceptor.getId()))
                .andExpect(jsonPath("$.subjectSectionId").value(programSection.getId()))
                .andExpect(jsonPath("$.courseSectionId").doesNotExist())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(assignments.existsByPersonIdAndSubjectSectionId(preceptor.getId(), programSection.getId())).isTrue();
        assertThat(auditCount("PRECEPTOR_ASSIGNED")).isEqualTo(1);
    }

    @Test
    void directorAssignsAPreceptorToACourseSection() throws Exception {
        Person preceptor = person("PRECEPTOR");

        mockMvc.perform(assign(person("DIRECTOR"), preceptor.getId(), null, courseSection.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseSectionId").value(courseSection.getId()));

        assertThat(assignments.existsByPersonIdAndCourseSectionId(preceptor.getId(), courseSection.getId())).isTrue();
    }

    @Test
    void severalPreceptorsCanShareASection() throws Exception {
        mockMvc.perform(assign(administrative, person("PRECEPTOR").getId(), programSection.getId(), null))
                .andExpect(status().isCreated());
        mockMvc.perform(assign(administrative, person("PRECEPTOR").getId(), programSection.getId(), null))
                .andExpect(status().isCreated());

        assertThat(assignments.findBySubjectSectionIdOrderByIdAsc(programSection.getId())).hasSize(2);
    }

    @Test
    void assigningAPersonWithoutThePreceptorRoleReturnsConflictByCode() throws Exception {
        Person teacher = person("DOCENTE");

        mockMvc.perform(assign(administrative, teacher.getId(), programSection.getId(), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRECEPTOR_ROLE_REQUIRED"));

        assertThat(assignments.findBySubjectSectionIdOrderByIdAsc(programSection.getId())).isEmpty();
    }

    @Test
    void assigningTheSamePreceptorTwiceReturnsConflictByCode() throws Exception {
        Person preceptor = person("PRECEPTOR");
        mockMvc.perform(assign(administrative, preceptor.getId(), null, courseSection.getId()))
                .andExpect(status().isCreated());

        mockMvc.perform(assign(administrative, preceptor.getId(), null, courseSection.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRECEPTOR_ASSIGNMENT_ALREADY_EXISTS"));

        assertThat(assignments.findByCourseSectionIdOrderByIdAsc(courseSection.getId())).hasSize(1);
        assertThat(auditCount("PRECEPTOR_ASSIGNED")).isEqualTo(1);
    }

    @Test
    void assigningToAMissingSectionReturnsNotFound() throws Exception {
        Person preceptor = person("PRECEPTOR");

        mockMvc.perform(assign(administrative, preceptor.getId(), 999_999_999L, null))
                .andExpect(status().isNotFound());
        mockMvc.perform(assign(administrative, preceptor.getId(), null, 999_999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void assigningWithoutExactlyOneSectionReturnsBadRequestByCode() throws Exception {
        Person preceptor = person("PRECEPTOR");

        mockMvc.perform(assign(administrative, preceptor.getId(), null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRECEPTOR_ASSIGNMENT_TARGET_INVALID"));
        mockMvc.perform(assign(administrative, preceptor.getId(), programSection.getId(), courseSection.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRECEPTOR_ASSIGNMENT_TARGET_INVALID"));
    }

    @Test
    void listingReturnsOnlyTheAssignmentsOfTheRequestedSection() throws Exception {
        Person first = person("PRECEPTOR");
        Person second = person("PRECEPTOR");
        save(first, programSection.getId(), null);
        save(second, programSection.getId(), null);
        save(first, null, courseSection.getId());

        mockMvc.perform(get("/preceptor-assignments").param("subjectSectionId", programSection.getId().toString())
                        .header("Authorization", bearer(administrative)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].personId").value(first.getId()))
                .andExpect(jsonPath("$[1].personId").value(second.getId()));
        mockMvc.perform(get("/preceptor-assignments").param("courseSectionId", courseSection.getId().toString())
                        .header("Authorization", bearer(administrative)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listingWithoutExactlyOneSectionReturnsBadRequestByCode() throws Exception {
        mockMvc.perform(get("/preceptor-assignments").header("Authorization", bearer(administrative)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRECEPTOR_ASSIGNMENT_TARGET_INVALID"));
    }

    @Test
    void unassigningTheLastPreceptorOfASectionIsAllowedAndAudited() throws Exception {
        PreceptorAssignment assignment = save(person("PRECEPTOR"), programSection.getId(), null);

        mockMvc.perform(delete("/preceptor-assignments/" + assignment.getId()).header("Authorization", bearer(administrative)))
                .andExpect(status().isNoContent());

        assertThat(assignments.findBySubjectSectionIdOrderByIdAsc(programSection.getId())).isEmpty();
        assertThat(auditCount("PRECEPTOR_UNASSIGNED")).isEqualTo(1);
    }

    @Test
    void unassigningAnUnknownAssignmentReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/preceptor-assignments/999999999").header("Authorization", bearer(administrative)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unassigningACourseResponsibleThroughThisEndpointReturnsNotFound() throws Exception {
        PreceptorAssignment responsible = assignments.save(PreceptorAssignment.builder()
                .personId(person("PRECEPTOR").getId()).courseId(courseSection.getCourseSubject().getCourse().getId()).createdBy(administrative.getId()).build());

        mockMvc.perform(delete("/preceptor-assignments/" + responsible.getId()).header("Authorization", bearer(administrative)))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherAndStudentAreForbiddenFromEveryOperation() throws Exception {
        PreceptorAssignment assignment = save(person("PRECEPTOR"), programSection.getId(), null);
        Person preceptor = person("PRECEPTOR");

        for (String role : new String[] {"DOCENTE", "ALUMNO"}) {
            Person actor = person(role);
            mockMvc.perform(get("/preceptor-assignments").param("subjectSectionId", programSection.getId().toString())
                            .header("Authorization", bearer(actor)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(assign(actor, preceptor.getId(), programSection.getId(), null))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/preceptor-assignments/" + assignment.getId()).header("Authorization", bearer(actor)))
                    .andExpect(status().isForbidden());
        }
        assertThat(assignments.existsById(assignment.getId())).isTrue();
    }

    private MockHttpServletRequestBuilder assign(Person actor, Long personId, Long subjectSectionId, Long courseSectionId) {
        String body = "{\"personId\":" + personId
                + (subjectSectionId == null ? "" : ",\"subjectSectionId\":" + subjectSectionId)
                + (courseSectionId == null ? "" : ",\"courseSectionId\":" + courseSectionId) + "}";
        return post("/preceptor-assignments").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Authorization", bearer(actor));
    }

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private PreceptorAssignment save(Person preceptor, Long subjectSectionId, Long courseSectionId) {
        PreceptorAssignment saved = assignments.saveAndFlush(PreceptorAssignment.builder()
                .personId(preceptor.getId()).subjectSectionId(subjectSectionId).courseSectionId(courseSectionId)
                .createdBy(administrative.getId()).build());
        entityManager.clear();
        return saved;
    }

    private long auditCount(String action) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_type = 'PRECEPTOR_ASSIGNMENT'",
                Long.class, action);
    }

    private Person person(String roleName) {
        String key = "pa" + System.nanoTime() + SEQUENCE.incrementAndGet();
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(key);
        person.setUsername(key);
        person.setEmail(key + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private CourseSubjectSection persistCourseSection() {
        Course course = new Course();
        course.setName("Curso " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        entityManager.persist(course);
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Modulo");
        entityManager.persist(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision " + System.nanoTime());
        section.setCourseSubject(module);
        section.setTerm(term);
        section.setCapacity(20);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        entityManager.flush();
        return section;
    }
}
