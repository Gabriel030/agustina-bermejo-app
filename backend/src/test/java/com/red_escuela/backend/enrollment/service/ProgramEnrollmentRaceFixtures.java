package com.red_escuela.backend.enrollment.service;

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
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;

public class ProgramEnrollmentRaceFixtures {

    private final EntityManager entityManager;
    private final CycleTestFixtures cycleFixtures;

    public ProgramEnrollmentRaceFixtures(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.cycleFixtures = new CycleTestFixtures(entityManager);
    }

    public CycleTestFixtures cycle() {
        return cycleFixtures;
    }

    public Term term() {
        return cycleFixtures.term(2040 + (int) (System.nanoTime() % 50));
    }

    public AcademicOfferingCycle cycleOf(Program program, Term term) {
        return cycleFixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
    }

    public SubjectSection section(Subject subject, Term term, AcademicOfferingCycle cycle, int capacity) {
        SubjectSection section = cycleFixtures.section(subject, term, StatusType.ACTIVE, cycle);
        section.setCapacity(capacity);
        return section;
    }

    public ProgramEnrollment programEnrollment(Program program) {
        Institution institution = new Institution();
        institution.setName("Instituto " + System.nanoTime());
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(cycleFixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        entityManager.persist(programEnrollment);
        return programEnrollment;
    }

    public SubjectSectionEnrollment sectionEnrollment(ProgramEnrollment programEnrollment, SubjectSection section,
            EnrollmentStatus status, LocalDateTime enrolledAt) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(programEnrollment.getStudent());
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(enrolledAt);
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }
}
