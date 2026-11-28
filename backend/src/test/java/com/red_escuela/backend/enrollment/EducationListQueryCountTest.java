package com.red_escuela.backend.enrollment;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.controller.MeEnrollmentController;
import com.red_escuela.backend.enrollment.dto.SectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.identity.api.StudentAttendanceRisk;
import com.red_escuela.backend.identity.dto.StudentResponse;
import com.red_escuela.backend.identity.dto.StaffResponse;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.specialist.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.identity.usecase.staff.ListStaffCommand;
import com.red_escuela.backend.identity.usecase.staff.ListStaffUseCase;
import com.red_escuela.backend.identity.usecase.student.ListStudentsCommand;
import com.red_escuela.backend.identity.usecase.student.ListStudentsUseCase;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EducationListQueryCountTest extends AbstractIntegrationTest {
    private static final int MANY = 12;
    private static final int STUDENT_LIST_QUERIES = 6;
    private static final int STAFF_LIST_QUERIES = 4;
    private static final int OWN_SECTION_ENROLLMENTS_QUERIES = 4;
    private static final int ATTENDANCE_SUMMARY_QUERIES = 3;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ListStudentsUseCase listStudentsUseCase;

    @Autowired
    private ListStaffUseCase listStaffUseCase;

    @Autowired
    private StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;

    @Autowired
    private MeEnrollmentController meEnrollmentController;

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    @Autowired
    private RoleRepository roleRepository;

    private int sequence = 70000000;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentListQueryCountDoesNotGrowWithStudents() {
        Institution institution = persistInstitution();
        for (int i = 0; i < MANY; i++) {
            persistStudent(institution).getPerson().getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
        }
        entityManager.flush();
        entityManager.clear();

        List<StudentResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory, STUDENT_LIST_QUERIES,
                () -> listStudentsUseCase.list(new ListStudentsCommand(null, "CONFIRMADO", null, 0, 100, false)).items());

        assertThat(responses).hasSize(MANY);
        assertThat(responses).allSatisfy(response -> assertThat(response.dni()).isNotNull());
    }

    @Test
    void studentListWithActiveEnrollmentsQueryCountDoesNotGrowWithEnrollments() {
        Fixture fixture = persistEnrollments(MANY);
        fixture.student().getPerson().getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
        for (int i = 0; i < MANY; i++) {
            persistStudent(persistInstitution()).getPerson().getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
        }
        entityManager.flush();
        entityManager.clear();

        List<StudentResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory, STUDENT_LIST_QUERIES,
                () -> listStudentsUseCase.list(new ListStudentsCommand(null, "CONFIRMADO", null, 0, 100, false)).items());

        assertThat(responses).hasSize(MANY + 1);
        assertThat(responses).allSatisfy(response -> assertThat(response.missingRequiredDocuments()).hasSize(3));
    }

    @Test
    void studentListSignalsAttendanceRiskFromActiveSectionEnrollments() {
        Long nearLimitStudentId = persistStudentWithAbsences(2, 10);
        Long freeStudentId = persistStudentWithAbsences(3, 10);
        Long safeStudentId = persistStudentWithAbsences(1, 10);
        entityManager.flush();
        entityManager.clear();

        List<StudentResponse> responses = listStudentsUseCase.list(new ListStudentsCommand(null, "CONFIRMADO", null, 0, 100, false)).items();

        assertThat(riskOf(responses, nearLimitStudentId)).isEqualTo(StudentAttendanceRisk.NEAR_LIMIT);
        assertThat(riskOf(responses, freeStudentId)).isEqualTo(StudentAttendanceRisk.FREE);
        assertThat(riskOf(responses, safeStudentId)).isEqualTo(StudentAttendanceRisk.NONE);
    }

    private StudentAttendanceRisk riskOf(List<StudentResponse> responses, Long studentId) {
        return responses.stream().filter(response -> response.id().equals(studentId)).findFirst().orElseThrow().attendanceRisk();
    }

    private Long persistStudentWithAbsences(int absences, int classes) {
        Fixture fixture = persistEnrollments(1);
        fixture.student().getPerson().getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
        SubjectSectionEnrollment enrollment = entityManager
                .createQuery("select e from SubjectSectionEnrollment e where e.student.id = :studentId", SubjectSectionEnrollment.class)
                .setParameter("studentId", fixture.student().getId()).getSingleResult();
        Instructor recorder = new Instructor();
        recorder.setPerson(persistPerson());
        entityManager.persist(recorder);
        for (int i = 0; i < classes; i++) {
            Attendance attendance = new Attendance();
            attendance.setProgramSubjectSectionEnrollment(enrollment);
            attendance.setClassDate(java.time.LocalDate.of(2026, 3, 1).plusDays(i));
            attendance.setStatus(i < absences ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT);
            attendance.setRecordedBy(recorder);
            entityManager.persist(attendance);
        }
        return fixture.student().getId();
    }

    @Test
    void staffListQueryCountDoesNotGrowWithStaff() {
        Person director = persistPerson();
        director.getRoles().add(roleRepository.findByName("DIRECTOR").orElseThrow());
        for (int i = 0; i < MANY; i++) {
            Person teacher = persistPerson();
            teacher.getRoles().add(roleRepository.findByName("DOCENTE").orElseThrow());
        }
        entityManager.flush();
        entityManager.clear();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(director.getId()), null));

        List<StaffResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory, STAFF_LIST_QUERIES,
                () -> listStaffUseCase.list(new ListStaffCommand(null, null, null)).stream()
                        .map(person -> StaffResponse.from(person, staffRoleAssignmentPolicy.resolveStaffRole(person))).toList());

        assertThat(responses).hasSize(MANY);
        assertThat(responses).allSatisfy(response -> assertThat(response.role()).isEqualTo("DOCENTE"));
    }

    @Test
    void ownSectionEnrollmentsQueryCountDoesNotGrowWithEnrollments() {
        Fixture fixture = persistEnrollments(MANY);
        entityManager.flush();
        entityManager.clear();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(String.valueOf(fixture.student().getPerson().getId()), null));

        List<SectionEnrollmentResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                OWN_SECTION_ENROLLMENTS_QUERIES, meEnrollmentController::mine);

        assertThat(responses).hasSize(MANY);
        assertThat(responses).allSatisfy(response -> assertThat(response.instructorTitular()).isNotNull());
    }

    @Test
    void attendanceSummaryQueryCountDoesNotGrowWithEnrollments() {
        Fixture fixture = persistEnrollments(MANY);
        entityManager.flush();
        entityManager.clear();

        List<SubjectAttendanceSummaryResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                ATTENDANCE_SUMMARY_QUERIES,
                () -> programEnrollmentService.attendanceSummaryBySubject(fixture.programEnrollment().getId()));

        assertThat(responses).hasSize(MANY);
    }

    private record Fixture(Student student, ProgramEnrollment programEnrollment) {
    }

    private Fixture persistEnrollments(int count) {
        Institution institution = persistInstitution();
        Student student = persistStudent(institution);
        Program program = new Program();
        program.setName("Perf program");
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        for (int i = 0; i < count; i++) {
            Subject subject = new Subject();
            subject.setCode(TestSubjectCodes.next());
            subject.setName("Subject " + subject.getCode());
            entityManager.persist(subject);
            SubjectSection section = new SubjectSection();
            section.setName("Section " + i);
            section.setSubject(subject);
            section.setTerm(term);
            section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            Instructor instructor = new Instructor();
            instructor.setPerson(persistPerson());
            entityManager.persist(instructor);
            SubjectSectionInstructor assignment = new SubjectSectionInstructor();
            assignment.setSection(section);
            assignment.setInstructor(instructor);
            assignment.setRole(InstructorRole.LEAD);
            entityManager.persist(assignment);
            SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgramEnrollment(programEnrollment);
            enrollment.setSubjectSection(section);
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);
        }
        return new Fixture(student, programEnrollment);
    }

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Perf institute");
        entityManager.persist(institution);
        return institution;
    }

    private Student persistStudent(Institution institution) {
        Student student = new Student();
        student.setPerson(persistPerson());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Person persistPerson() {
        String dni = String.valueOf(sequence++);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Name" + dni);
        person.setLastname("Last" + dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }
}
