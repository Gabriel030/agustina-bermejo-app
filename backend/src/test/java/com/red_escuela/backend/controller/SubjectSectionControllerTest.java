package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.AssignInstructorRequest;
import com.red_escuela.backend.dto.CreateSectionRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.CurricularTerm;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.InstructorRole;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.RoleRepository;
import com.red_escuela.backend.repository.SubjectSectionRepository;
import com.red_escuela.backend.service.JwtService;
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
    void crearComisionSobreSubjectCompartidoPorDosCarrerasQuedaComoUnaSolaFila() throws Exception {
        Subject subject = new Subject();
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
        term.setType("1C");
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
        // La comisión no tiene ninguna referencia a Program: el modelo la desacopló de la carrera.
        assertThat(sections.get(0).getSubject().getId()).isEqualTo(subject.getId());
    }

    @Test
    void asignarElMismoDocenteConElMismoRolDosVecesDevuelve409() throws Exception {
        Subject subject = new Subject();
        subject.setName("Subject para docentes");
        entityManager.persist(subject);

        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026 2C");
        term.setType("2C");
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
    void crearComisionSinRolPreceptorNiAdministrativoDevuelve403() throws Exception {
        Subject subject = new Subject();
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
    void listarSeccionesConCualquierRolAutenticadoDevuelve200() throws Exception {
        Subject subject = new Subject();
        subject.setName("Subject para listar");
        entityManager.persist(subject);
        entityManager.flush();

        String token = tokenWithRole("alumno-lector", "ALUMNO");

        mockMvc.perform(get("/subjects/{id}/sections", subject.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
