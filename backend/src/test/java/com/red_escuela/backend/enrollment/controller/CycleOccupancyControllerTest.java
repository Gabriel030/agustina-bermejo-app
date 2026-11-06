package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CycleOccupancyControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    @Test
    void countsOnlySeatOccupyingEnrollmentsAndSplitsTheWaitlist() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        Term term = fixtures.term(2041);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        SubjectSection emptySection = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        emptySection.setCapacity(5);
        Institution institution = new Institution();
        institution.setName("Instituto");
        entityManager.persist(institution);
        persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.ENROLLED);
        persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.IN_PROGRESS);
        persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.WAITLISTED);
        persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.WITHDRAWN);
        entityManager.flush();

        mockMvc.perform(get("/academic-offering-cycles/{id}/occupancy", cycle.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-occ", "PRECEPTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleId").value(cycle.getId()))
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andExpect(jsonPath("$.sections[?(@.sectionId==" + section.getId() + ")].enrolled").value(2))
                .andExpect(jsonPath("$.sections[?(@.sectionId==" + section.getId() + ")].available").value(18))
                .andExpect(jsonPath("$.sections[?(@.sectionId==" + section.getId() + ")].waitlisted").value(1))
                .andExpect(jsonPath("$.sections[?(@.sectionId==" + section.getId() + ")].kind").value("PROGRAM"))
                .andExpect(jsonPath("$.sections[?(@.sectionId==" + emptySection.getId() + ")].enrolled").value(0))
                .andExpect(jsonPath("$.sections[?(@.sectionId==" + emptySection.getId() + ")].available").value(5));
    }

    @Test
    void docenteIsForbiddenAndDirectorAllowed() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        AcademicOfferingCycle cycle = fixtures.programCycle(
                fixtures.program(), fixtures.term(2042), AcademicOfferingCycleStatus.APPROVED);
        entityManager.flush();

        mockMvc.perform(get("/academic-offering-cycles/{id}/occupancy", cycle.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("docente-occ", "DOCENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/academic-offering-cycles/{id}/occupancy", cycle.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("director-occ", "DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(0));
    }

    @Test
    void unknownCycleReturns404() throws Exception {
        mockMvc.perform(get("/academic-offering-cycles/{id}/occupancy", 999999L)
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-occ2", "PRECEPTOR")))
                .andExpect(status().isNotFound());
    }

    private void persistEnrollment(CycleTestFixtures fixtures, Institution institution, Program program,
                                   SubjectSection section, EnrollmentStatus status) {
        Person person = fixtures.person();
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setStudent(student);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
    }

    private String tokenWithRole(String username, String roleName) {
        Person person = new CycleTestFixtures(entityManager).person();
        person.setUsername(username);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }
}
