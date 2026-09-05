package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.AssignInstructorRequest;
import com.red_escuela.backend.dto.CreateSectionRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseSubject;
import com.red_escuela.backend.entity.CourseSubjectSection;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.InstructorRole;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.repository.RoleRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mismo patrón que SubjectSectionControllerTest, sobre el lado Course del modelo
 * (CourseSubjectSection, que a diferencia de SubjectSection sí sigue atada a un único
 * Course/CourseSubject — decisión 9 de la spec, los módulos de un curso no se comparten).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CourseSectionControllerTest extends AbstractIntegrationTest {

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
    private CourseSubjectSectionRepository courseSubjectSectionRepository;

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

    private CourseSubject persistCourseSubject(String name) {
        Term courseTerm = new Term();
        courseTerm.setYear(2026);
        courseTerm.setDescription("Term del curso");
        courseTerm.setType("1C");
        courseTerm.setStartDate(LocalDateTime.now());
        courseTerm.setEndDate(LocalDateTime.now().plusMonths(2));
        entityManager.persist(courseTerm);

        Course course = new Course();
        course.setName("Curso para " + name);
        course.setTerm(courseTerm);
        course.setCapacity(20);
        course.setIsActive(StatusType.ACTIVE);
        entityManager.persist(course);

        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName(name);
        courseSubject.setStatus(StatusType.ACTIVE);
        entityManager.persist(courseSubject);
        return courseSubject;
    }

    private Term persistSectionTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("Term de la comisión");
        term.setType("1C");
        term.setStartDate(LocalDateTime.now());
        term.setEndDate(LocalDateTime.now().plusMonths(4));
        entityManager.persist(term);
        return term;
    }

    @Test
    void createSectionOfModuleWithRolePreceptorReturns201() throws Exception {
        CourseSubject courseSubject = persistCourseSubject("Módulo A");
        Term term = persistSectionTerm();
        entityManager.flush();

        String token = tokenWithRole("preceptor-course-section1", "PRECEPTOR");
        CreateSectionRequest request = new CreateSectionRequest("Comisión Única", term.getId(), "Martes 19hs", 25, null);

        mockMvc.perform(post("/course-subjects/{id}/sections", courseSubject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseSubjectId").value(courseSubject.getId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        List<CourseSubjectSection> sections = courseSubjectSectionRepository.findByCourseSubjectId(courseSubject.getId());
        assertThat(sections).hasSize(1);
    }

    @Test
    void createSectionWithoutRolePreceptorNorAdministrativeReturns403() throws Exception {
        CourseSubject courseSubject = persistCourseSubject("Módulo sin permiso");
        entityManager.flush();

        String token = tokenWithRole("alumno-course-section1", "ALUMNO");
        CreateSectionRequest request = new CreateSectionRequest("Comisión A", 1L, null, 10, null);

        mockMvc.perform(post("/course-subjects/{id}/sections", courseSubject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listSectionsWithAnyRoleAuthenticatedReturns200() throws Exception {
        CourseSubject courseSubject = persistCourseSubject("Módulo para list");
        entityManager.flush();

        String token = tokenWithRole("alumno-course-section2", "ALUMNO");
        mockMvc.perform(get("/course-subjects/{id}/sections", courseSubject.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void assignTheSameTeacherWithTheSameRoleTwoTimesReturns409() throws Exception {
        CourseSubject courseSubject = persistCourseSubject("Módulo para docentes");
        Term term = persistSectionTerm();

        CourseSubjectSection section = new CourseSubjectSection();
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setName("Comisión con docente");
        section.setCapacity(20);
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        entityManager.persist(section);

        Instructor instructor = persistInstructor("docente-course-duplicado");
        entityManager.flush();

        String token = tokenWithRole("preceptor-course-docentes", "PRECEPTOR");
        AssignInstructorRequest request = new AssignInstructorRequest(
                instructor.getId(), InstructorRole.LEAD, null, null, null, null);

        mockMvc.perform(post("/course-sections/{id}/instructors", section.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/course-sections/{id}/instructors", section.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void listTeachersOfASectionReturns200() throws Exception {
        CourseSubject courseSubject = persistCourseSubject("Módulo para list docentes");
        Term term = persistSectionTerm();

        CourseSubjectSection section = new CourseSubjectSection();
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setName("Comisión con docente listado");
        section.setCapacity(20);
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        entityManager.persist(section);

        Instructor instructor = persistInstructor("docente-course-listado");
        entityManager.flush();

        String token = tokenWithRole("preceptor-course-list-docentes", "PRECEPTOR");
        mockMvc.perform(post("/course-sections/{id}/instructors", section.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new AssignInstructorRequest(
                                instructor.getId(), InstructorRole.LEAD, null, null, null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/course-sections/{id}/instructors", section.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void removeTeacherOfASectionThatNotToItCorrespondsReturns404() throws Exception {
        CourseSubject courseSubject = persistCourseSubject("Módulo para cruce");
        Term term = persistSectionTerm();

        CourseSubjectSection sectionReal = new CourseSubjectSection();
        sectionReal.setCourseSubject(courseSubject);
        sectionReal.setTerm(term);
        sectionReal.setName("Comisión Real");
        sectionReal.setCapacity(20);
        sectionReal.setStatus(StatusType.ACTIVE);
        sectionReal.setCreatedAt(LocalDateTime.now());
        entityManager.persist(sectionReal);

        CourseSubjectSection otraSeccion = new CourseSubjectSection();
        otraSeccion.setCourseSubject(courseSubject);
        otraSeccion.setTerm(term);
        otraSeccion.setName("Otra Comisión");
        otraSeccion.setCapacity(20);
        otraSeccion.setStatus(StatusType.ACTIVE);
        otraSeccion.setCreatedAt(LocalDateTime.now());
        entityManager.persist(otraSeccion);

        Instructor instructor = persistInstructor("docente-course-cruce");
        entityManager.flush();

        String token = tokenWithRole("preceptor-course-cruce", "PRECEPTOR");
        String body = mockMvc.perform(post("/course-sections/{id}/instructors", otraSeccion.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new AssignInstructorRequest(
                                instructor.getId(), InstructorRole.LEAD, null, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long assignmentId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/course-sections/{id}/instructors/{aid}", sectionReal.getId(), assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeTeacherNonexistentReturns404() throws Exception {
        CourseSubject courseSubject = persistCourseSubject("Módulo para quitar inexistente");
        Term term = persistSectionTerm();

        CourseSubjectSection section = new CourseSubjectSection();
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setName("Comisión");
        section.setCapacity(20);
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        entityManager.persist(section);
        entityManager.flush();

        String token = tokenWithRole("preceptor-course-quitar-inexistente", "PRECEPTOR");
        mockMvc.perform(delete("/course-sections/{id}/instructors/{aid}", section.getId(), 999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivateAndReactivateSectionWorksAndTheListingFiltersByStatus() throws Exception {
        CourseSubject courseSubject = persistCourseSubject("Módulo para baja de comisión");
        Term term = persistSectionTerm();
        entityManager.flush();

        String token = tokenWithRole("preceptor-course-baja-comision", "PRECEPTOR");
        CreateSectionRequest request = new CreateSectionRequest("Comisión Baja", term.getId(), null, 10, null);
        String body = mockMvc.perform(post("/course-subjects/{id}/sections", courseSubject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long sectionId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/course-sections/{id}/deactivate", sectionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/course-subjects/{id}/sections", courseSubject.getId())
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + sectionId + ")]").isEmpty());
        mockMvc.perform(get("/course-subjects/{id}/sections", courseSubject.getId())
                        .param("status", "INACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + sectionId + ")]").isNotEmpty());

        mockMvc.perform(post("/course-sections/{id}/activate", sectionId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/course-sections/{id}", sectionId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void editSectionUpdatesFields() throws Exception {
        CourseSubject courseSubject = persistCourseSubject("Módulo para editar comisión");
        Term term = persistSectionTerm();
        entityManager.flush();

        String token = tokenWithRole("preceptor-course-editar", "PRECEPTOR");
        String body = mockMvc.perform(post("/course-subjects/{id}/sections", courseSubject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateSectionRequest("Comisión Original", term.getId(), null, 10, null))))
                .andReturn().getResponse().getContentAsString();
        Long sectionId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/course-sections/{id}", sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\":\"Comisión Renombrada\",\"capacity\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Comisión Renombrada"))
                .andExpect(jsonPath("$.capacity").value(30));
    }
}
