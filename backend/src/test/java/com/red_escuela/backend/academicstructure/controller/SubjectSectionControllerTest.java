package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.config.TestSubjectCodes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubjectSectionControllerTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SubjectSectionRepository subjectSectionRepository;

    private String tokenWithRole(String username, String roleName) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);

        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);

        entityManager.persist(person);
        entityManager.flush();

        return jwtService.generateAccessToken(person);
    }

    private Instructor persistInstructor(String username) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Docente");
        person.setLastname(username);
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);

        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        return instructor;
    }

    @Test
    void creatingSectionOverASubjectSharedByTwoProgramsEndsUpAsOneSingleRow() throws Exception {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Inglés Compartido");
        entityManager.persist(subject);

        Program carreraA = new Program();
        carreraA.setName("Tecnicatura Compartida A");
        carreraA.setTotalYears(3);
        entityManager.persist(carreraA);

        Program carreraB = new Program();
        carreraB.setName("Tecnicatura Compartida B");
        carreraB.setTotalYears(3);
        entityManager.persist(carreraB);

        ProgramSubject entradaA = new ProgramSubject();
        entradaA.setProgram(carreraA);
        entradaA.setSubject(subject);
        entradaA.setCurricularYear(1);
        entradaA.setCurricularTerm(CurricularTerm.FIRST);
        entradaA.setCredits(5);
        entityManager.persist(entradaA);

        ProgramSubject entradaB = new ProgramSubject();
        entradaB.setProgram(carreraB);
        entradaB.setSubject(subject);
        entradaB.setCurricularYear(2);
        entradaB.setCurricularTerm(CurricularTerm.SECOND);
        entradaB.setCredits(3);
        entityManager.persist(entradaB);

        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026 1C");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(4));
        entityManager.persist(term);
        entityManager.flush();

        String token = tokenWithRole("preceptor-shared", "PRECEPTOR");
        CreateSectionRequest request = new CreateSectionRequest("Comisión Única", term.getId(), "Lunes 18hs", 30, null);

        mockMvc.perform(post("/subjects/{id}/sections", subject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectId").value(subject.getId()));

        List<SubjectSection> sections = subjectSectionRepository.findBySubjectId(subject.getId());
        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).getSubject().getId()).isEqualTo(subject.getId());
    }

    @Test
    void assigningTheSameInstructorWithTheSameRoleTwiceReturns409() throws Exception {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Subject para docentes");
        entityManager.persist(subject);

        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026 2C");
        term.setType(TermType.SECOND_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(4));
        entityManager.persist(term);

        SubjectSection section = new SubjectSection();
        section.setSubject(subject);
        section.setTerm(term);
        section.setName("Comisión A");
        section.setCapacity(20);
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        entityManager.persist(section);

        Instructor instructor = persistInstructor("docente-duplicado");
        entityManager.flush();

        String token = tokenWithRole("preceptor-docentes", "PRECEPTOR");
        AssignInstructorRequest request = new AssignInstructorRequest(
                instructor.getId(), InstructorRole.LEAD, null, null, null, null);

        mockMvc.perform(post("/program-sections/{id}/instructors", section.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/program-sections/{id}/instructors", section.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingSectionWithoutPreceptorOrAdministrativeRoleReturns403() throws Exception {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Subject sin permiso");
        entityManager.persist(subject);
        entityManager.flush();

        String token = tokenWithRole("alumno-sin-permiso", "ALUMNO");
        CreateSectionRequest request = new CreateSectionRequest("Comisión A", 1L, null, 10, null);

        mockMvc.perform(post("/subjects/{id}/sections", subject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingSectionWithNonExistentEvaluationSchemeReturns404() throws Exception {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Subject con esquema inválido");
        entityManager.persist(subject);

        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026 term eval");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(4));
        entityManager.persist(term);
        entityManager.flush();

        String token = tokenWithRole("preceptor-eval-invalido", "PRECEPTOR");
        CreateSectionRequest request = new CreateSectionRequest("Comisión A", term.getId(), null, 10, 999999L);

        mockMvc.perform(post("/subjects/{id}/sections", subject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removingInstructorFromASectionTheyDoNotBelongToReturns404() throws Exception {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Subject para cruce de comisiones");
        entityManager.persist(subject);

        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026 term cruce");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(4));
        entityManager.persist(term);

        SubjectSection sectionReal = new SubjectSection();
        sectionReal.setSubject(subject);
        sectionReal.setTerm(term);
        sectionReal.setName("Comisión Real");
        sectionReal.setCapacity(20);
        sectionReal.setStatus(StatusType.ACTIVE);
        sectionReal.setCreatedAt(LocalDateTime.now());
        entityManager.persist(sectionReal);

        SubjectSection otraSeccion = new SubjectSection();
        otraSeccion.setSubject(subject);
        otraSeccion.setTerm(term);
        otraSeccion.setName("Otra Comisión");
        otraSeccion.setCapacity(20);
        otraSeccion.setStatus(StatusType.ACTIVE);
        otraSeccion.setCreatedAt(LocalDateTime.now());
        entityManager.persist(otraSeccion);

        Instructor instructor = persistInstructor("docente-cruce");
        entityManager.flush();

        String token = tokenWithRole("preceptor-cruce", "PRECEPTOR");
        AssignInstructorRequest request = new AssignInstructorRequest(
                instructor.getId(), InstructorRole.LEAD, null, null, null, null);

        String body = mockMvc.perform(post("/program-sections/{id}/instructors", otraSeccion.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long assignmentId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/program-sections/{id}/instructors/{aid}", sectionReal.getId(), assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingSectionsWithAnyAuthenticatedRoleReturns200() throws Exception {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Subject para list");
        entityManager.persist(subject);
        entityManager.flush();

        String token = tokenWithRole("alumno-lector", "ALUMNO");

        mockMvc.perform(get("/subjects/{id}/sections", subject.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatingAndReactivatingSectionWorksAndTheListingFiltersByStatus() throws Exception {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Subject para baja de comisión");
        entityManager.persist(subject);

        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026 term baja comisión");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(4));
        entityManager.persist(term);
        entityManager.flush();

        String token = tokenWithRole("preceptor-baja-comision", "PRECEPTOR");
        CreateSectionRequest request = new CreateSectionRequest("Comisión Baja", term.getId(), null, 10, null);
        String body = mockMvc.perform(post("/subjects/{id}/sections", subject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        Long sectionId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/program-sections/{id}/deactivate", sectionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/subjects/{id}/sections", subject.getId())
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + sectionId + ")]").isEmpty());
        mockMvc.perform(get("/subjects/{id}/sections", subject.getId())
                        .param("status", "INACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + sectionId + ")]").isNotEmpty());

        mockMvc.perform(post("/program-sections/{id}/activate", sectionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/program-sections/{id}", sectionId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void listingInstructorsOfASectionReturns200() throws Exception {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Subject para list docentes");
        entityManager.persist(subject);

        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026 term list docentes");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(4));
        entityManager.persist(term);

        SubjectSection section = new SubjectSection();
        section.setSubject(subject);
        section.setTerm(term);
        section.setName("Comisión con docentes");
        section.setCapacity(20);
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        entityManager.persist(section);

        Instructor instructor = persistInstructor("docente-listado");
        entityManager.flush();

        String token = tokenWithRole("preceptor-list-docentes", "PRECEPTOR");
        mockMvc.perform(post("/program-sections/{id}/instructors", section.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new AssignInstructorRequest(
                                instructor.getId(), InstructorRole.LEAD, null, null, null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/program-sections/{id}/instructors", section.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].instructorId").value(instructor.getId()));
    }

    @Test
    void updatingSectionUpdatesFields() throws Exception {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Subject para editar comisión");
        entityManager.persist(subject);

        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026 term editar comisión");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(4));
        entityManager.persist(term);
        entityManager.flush();

        String token = tokenWithRole("preceptor-editar-comision", "PRECEPTOR");
        String body = mockMvc.perform(post("/subjects/{id}/sections", subject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateSectionRequest("Comisión Original", term.getId(), null, 10, null))))
                .andReturn().getResponse().getContentAsString();
        Long sectionId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/program-sections/{id}", sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\":\"Comisión Renombrada\",\"capacity\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Comisión Renombrada"))
                .andExpect(jsonPath("$.capacity").value(40));
    }
}
