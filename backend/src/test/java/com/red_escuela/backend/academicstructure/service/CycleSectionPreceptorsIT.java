package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CycleSectionPreceptorsIT extends AbstractIntegrationTest {

    @Autowired private AcademicOfferingCycleReader cycleReader;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void listingReturnsEachSectionsPreceptorsWithTheirPendingAttendanceRequestCount() {
        Seed seed = seedCycle(3);
        Person first = newPerson();
        Person second = newPerson();
        long firstAssignment = assign(first, seed.sections().get(0));
        long secondAssignment = assign(second, seed.sections().get(0));
        assign(first, seed.sections().get(1));
        addPendingRequests(seed.sections().get(0), 2);
        addPendingRequests(seed.sections().get(1), 1);
        addRequest(seed.sections().get(0), AttendanceChangeRequestStatus.APPROVED);

        Map<Long, CycleSectionResponse> bySection = listById(seed.cycleId());

        CycleSectionResponse zero = bySection.get(seed.sections().get(0));
        assertThat(zero.preceptors()).extracting(CycleSectionResponse.PreceptorInfo::assignmentId)
                .containsExactly(firstAssignment, secondAssignment);
        assertThat(zero.preceptors()).extracting(CycleSectionResponse.PreceptorInfo::personId)
                .containsExactly(first.getId(), second.getId());
        assertThat(zero.preceptors()).extracting(CycleSectionResponse.PreceptorInfo::pendingRequestCount)
                .containsExactly(2L, 2L);
        assertThat(zero.preceptors().get(0).personName()).isEqualTo(first.getName() + " " + first.getLastname());
        assertThat(bySection.get(seed.sections().get(1)).preceptors())
                .extracting(CycleSectionResponse.PreceptorInfo::pendingRequestCount).containsExactly(1L);
        assertThat(bySection.get(seed.sections().get(2)).preceptors()).isEmpty();
    }

    @Test
    void sectionPreceptorsAddAtMostAConstantNumberOfQueriesRegardlessOfSectionCount() {
        Seed seed = seedCycle(8);
        long without = countStatements(seed.cycleId());
        Person preceptor = newPerson();
        for (Long sectionId : seed.sections()) {
            assign(preceptor, sectionId);
            addPendingRequests(sectionId, 1);
        }

        long with = countStatements(seed.cycleId());

        assertThat(with - without).isLessThanOrEqualTo(3);
        assertThat(listById(seed.cycleId()).values())
                .allSatisfy(section -> assertThat(section.preceptors()).hasSize(1));
    }

    private long countStatements(Long cycleId) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            new TransactionTemplate(transactionManager).execute(status -> cycleReader.listCycleSections(cycleId));
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private Map<Long, CycleSectionResponse> listById(Long cycleId) {
        return new TransactionTemplate(transactionManager).execute(status -> cycleReader.listCycleSections(cycleId))
                .stream().collect(Collectors.toMap(CycleSectionResponse::id, Function.identity()));
    }

    private Seed seedCycle(int sectionCount) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Term term = fixtures.term(2100 + (int) (System.nanoTime() % 800));
            var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
            var subject = fixtures.subject();
            List<Long> sections = new ArrayList<>();
            for (int i = 0; i < sectionCount; i++) {
                SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
                sections.add(section.getId());
            }
            fixtures.flushAndClear();
            return new Seed(cycle.getId(), sections);
        });
    }

    private Person newPerson() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Person person = new CycleTestFixtures(entityManager).person();
            entityManager.flush();
            return person;
        });
    }

    private long assign(Person person, Long sectionId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.createNativeQuery("insert into preceptor_assignment (person_id, subject_section_id, created_by) values (?1, ?2, ?1)")
                    .setParameter(1, person.getId()).setParameter(2, sectionId).executeUpdate();
            return ((Number) entityManager.createNativeQuery("select max(id) from preceptor_assignment where person_id = ?1 and subject_section_id = ?2")
                    .setParameter(1, person.getId()).setParameter(2, sectionId).getSingleResult()).longValue();
        });
    }

    private void addPendingRequests(Long sectionId, int count) {
        for (int i = 0; i < count; i++) {
            addRequest(sectionId, AttendanceChangeRequestStatus.PENDING);
        }
    }

    private void addRequest(Long sectionId, AttendanceChangeRequestStatus requestStatus) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Instructor instructor = fixtures.instructor();
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(fixtures.person());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(fixtures.program());
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);
            SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgramEnrollment(programEnrollment);
            enrollment.setSubjectSection(entityManager.getReference(SubjectSection.class, sectionId));
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);
            Attendance attendance = new Attendance();
            attendance.setProgramSubjectSectionEnrollment(enrollment);
            attendance.setClassDate(LocalDate.of(2030, 3, 4));
            attendance.setStatus(AttendanceStatus.ABSENT);
            attendance.setRecordedBy(instructor);
            entityManager.persist(attendance);
            AttendanceChangeRequest request = new AttendanceChangeRequest();
            request.setAttendance(attendance);
            request.setRequestedBy(instructor);
            request.setPreviousStatus(AttendanceStatus.ABSENT);
            request.setRequestedStatus(AttendanceStatus.PRESENT);
            request.setReason("student arrived late and was marked absent");
            request.setStatus(requestStatus);
            entityManager.persist(request);
        });
    }

    private record Seed(Long cycleId, List<Long> sections) {
    }
}
