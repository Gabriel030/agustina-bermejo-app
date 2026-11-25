package com.red_escuela.backend.notification.broadcast.template;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
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

import java.time.LocalDateTime;

public final class PersonEnrollmentTestFixtures {

    private final EntityManager entityManager;
    private final CycleTestFixtures fixtures;
    private final Institution institution;
    private final Term term;

    public PersonEnrollmentTestFixtures(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.fixtures = new CycleTestFixtures(entityManager);
        this.institution = new Institution();
        this.institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        this.term = fixtures.term(2100 + (int) (System.nanoTime() % 800));
    }

    public Student studentOf(Person person) {
        var existing = entityManager.createQuery("select s from Student s where s.person.id = :personId", Student.class)
                .setParameter("personId", person.getId()).getResultList();
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    public void enroll(Student student, String programName, String subjectName, String sectionName,
                       LocalDateTime enrolledAt, EnrollmentStatus status) {
        Program program = fixtures.program();
        program.setName(programName);
        Subject subject = fixtures.subject();
        subject.setName(subjectName);
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        section.setName(sectionName);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(enrolledAt);
        entityManager.persist(programEnrollment);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(enrolledAt);
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        entityManager.flush();
    }
}
