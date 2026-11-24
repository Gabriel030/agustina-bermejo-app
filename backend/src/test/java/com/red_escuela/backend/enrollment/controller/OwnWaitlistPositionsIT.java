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
class OwnWaitlistPositionsIT extends AbstractIntegrationTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2045, 2, 3, 9, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    @Test
    void studentInThirdPlaceOfSevenSeesOwnPositionWithoutThirdPartyData() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        SubjectSection section = openSection(fixtures, program, 2045);
        Institution institution = institution();
        persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(1));
        persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(2));
        Student mine = persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(3)).student();
        for (int hour = 4; hour <= 7; hour++) {
            persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(hour));
        }
        entityManager.flush();

        mockMvc.perform(get("/me/waitlist-positions").header("Authorization", "Bearer " + studentToken(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("PROGRAM_SECTION"))
                .andExpect(jsonPath("$[0].queueId").value(section.getId()))
                .andExpect(jsonPath("$[0].position").value(3))
                .andExpect(jsonPath("$[0].totalWaiting").value(7))
                .andExpect(jsonPath("$[0].student").doesNotExist())
                .andExpect(jsonPath("$[0].documentNumber").doesNotExist())
                .andExpect(jsonPath("$[0].verdict").doesNotExist())
                .andExpect(jsonPath("$[0].prioritizedAt").doesNotExist());
    }

    @Test
    void prioritizedStudentMovesToFirstAndPushesTheOthersBack() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        SubjectSection section = openSection(fixtures, program, 2046);
        Institution institution = institution();
        persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(1));
        persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(2));
        persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(3));
        Student mine = persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(4)).student();
        WaitingEntry prioritizedEntry = persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(9));
        SubjectSectionEnrollment prioritized = prioritizedEntry.enrollment();
        prioritized.setWaitlistPrioritizedAt(BASE_TIME.plusHours(10));
        entityManager.flush();

        mockMvc.perform(get("/me/waitlist-positions").header("Authorization", "Bearer " + studentToken(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value(5))
                .andExpect(jsonPath("$[0].totalWaiting").value(5));

        mockMvc.perform(get("/me/waitlist-positions")
                        .header("Authorization", "Bearer " + studentToken(prioritizedEntry.student())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enrollmentId").value(prioritized.getId()))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].totalWaiting").value(5));
    }

    @Test
    void studentWithoutWaitingGetsEmptyListAndNeverOthersRows() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        SubjectSection section = openSection(fixtures, program, 2047);
        Institution institution = institution();
        persistWaiting(fixtures, institution, program, section, BASE_TIME.plusHours(1));
        Student outsider = student(fixtures, institution);
        entityManager.flush();

        mockMvc.perform(get("/me/waitlist-positions").header("Authorization", "Bearer " + studentToken(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void teacherAndStaffCannotReadOwnWaitlistPositions() throws Exception {
        for (String role : new String[]{"DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR"}) {
            mockMvc.perform(get("/me/waitlist-positions")
                            .header("Authorization", "Bearer " + tokenWithRole("staff-own-wl-" + role, role)))
                    .andExpect(status().isForbidden());
        }
    }

    private SubjectSection openSection(CycleTestFixtures fixtures, Program program, int year) {
        Term term = fixtures.term(year);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        return fixtures.section(subject, term, StatusType.ACTIVE, cycle);
    }

    private Institution institution() {
        Institution institution = new Institution();
        institution.setName("Instituto");
        entityManager.persist(institution);
        return institution;
    }

    private Student student(CycleTestFixtures fixtures, Institution institution) {
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private WaitingEntry persistWaiting(CycleTestFixtures fixtures, Institution institution, Program program,
                                        SubjectSection section, LocalDateTime enrolledAt) {
        Student student = student(fixtures, institution);
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
        enrollment.setStatus(EnrollmentStatus.WAITLISTED);
        enrollment.setEnrolledAt(enrolledAt);
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return new WaitingEntry(student, enrollment);
    }

    private record WaitingEntry(Student student, SubjectSectionEnrollment enrollment) {
    }

    private String studentToken(Student student) {
        Person person = student.getPerson();
        Role role = roleRepository.findByName("ALUMNO").orElseThrow();
        person.getRoles().add(role);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
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
