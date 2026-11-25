package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class ListenerAtomicityFixtures {

    private static final AtomicLong YEARS = new AtomicLong(2600);
    private static final LocalDateTime LONG_AFTER_ANY_TEST = LocalDateTime.of(2900, 1, 1, 0, 0);

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaction;
    private String failingTable;
    private String failingFunction;

    public ListenerAtomicityFixtures(EntityManager entityManager, JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public <T> T inOneTransaction(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }

    public void inOneTransaction(Runnable work) {
        transaction.executeWithoutResult(status -> work.run());
    }

    public Student seedStudent() {
        return inOneTransaction(() -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(new CycleTestFixtures(entityManager).person());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            entityManager.flush();
            return student;
        });
    }

    public ProgramEnrollment seedProgramEnrollment(Student student) {
        return inOneTransaction(() -> {
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(new CycleTestFixtures(entityManager).program());
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.now().minusDays(30));
            entityManager.persist(enrollment);
            entityManager.flush();
            return enrollment;
        });
    }

    public CourseEnrollment seedCourseEnrollment(Student student) {
        return inOneTransaction(() -> {
            Term term = new CycleTestFixtures(entityManager).term((int) YEARS.incrementAndGet());
            term.setEnrollmentEnd(LONG_AFTER_ANY_TEST);
            Course course = new Course();
            course.setName("Course " + YEARS.get());
            course.setTerm(term);
            course.setCapacity(10);
            course.setIsActive(StatusType.ACTIVE);
            course.setTeachingMode(TeachingMode.ONLINE);
            entityManager.persist(course);
            CourseEnrollment enrollment = new CourseEnrollment();
            enrollment.setStudent(student);
            enrollment.setCourse(course);
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.now().minusDays(8));
            entityManager.persist(enrollment);
            entityManager.flush();
            return enrollment;
        });
    }

    public void renameInstitutionOf(Student student, String name) {
        jdbcTemplate.update("update institution set name = ? where id = ?", name, student.getInstitution().getId());
    }

    public String institutionNameOf(Student student) {
        return jdbcTemplate.queryForObject("select name from institution where id = ?", String.class, student.getInstitution().getId());
    }

    public void failOn(String table, String operation, String condition, String message) {
        removeFailure();
        failingFunction = "fail_" + table + "_listener";
        failingTable = table;
        jdbcTemplate.execute("create or replace function " + failingFunction + "() returns trigger as $$ begin if " + condition
                + " then raise exception '" + message + "'; end if; return new; end $$ language plpgsql");
        jdbcTemplate.execute("create trigger " + failingFunction + " before " + operation + " on " + table + " for each row execute function " + failingFunction + "()");
    }

    public void removeFailure() {
        if (failingTable != null) {
            jdbcTemplate.execute("drop trigger if exists " + failingFunction + " on " + failingTable);
            jdbcTemplate.execute("drop function if exists " + failingFunction + "()");
            failingTable = null;
            failingFunction = null;
        }
    }
}
