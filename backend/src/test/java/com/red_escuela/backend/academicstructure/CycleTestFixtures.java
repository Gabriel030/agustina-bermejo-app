package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.EntityManager;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class CycleTestFixtures {

    private static final AtomicLong DNI_SEQUENCE = new AtomicLong(71_000_000L);

    private final EntityManager entityManager;

    public CycleTestFixtures(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Person person() {
        Address address = new Address();
        entityManager.persist(address);
        String dni = String.valueOf(DNI_SEQUENCE.getAndIncrement());
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername("cycle." + dni);
        person.setEmail("cycle." + dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    public Instructor instructor() {
        Instructor instructor = new Instructor();
        instructor.setPerson(person());
        entityManager.persist(instructor);
        return instructor;
    }

    public Term term(int year) {
        Term term = new Term();
        term.setYear(year);
        term.setDescription("Ciclo lectivo " + year + " " + UUID.randomUUID());
        term.setType(TermType.ANNUAL);
        term.setStartDate(LocalDateTime.of(year, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(year, 12, 15, 0, 0));
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    public Classroom classroom(int capacity) {
        Classroom classroom = new Classroom();
        classroom.setName("Aula " + UUID.randomUUID());
        classroom.setCapacity(capacity);
        entityManager.persist(classroom);
        return classroom;
    }

    public Program program() {
        Program program = new Program();
        program.setName("Carrera " + UUID.randomUUID());
        program.setTotalYears(3);
        entityManager.persist(program);
        return program;
    }

    public Subject subject() {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia " + UUID.randomUUID());
        subject.setStatus(SubjectStatus.ACTIVE);
        entityManager.persist(subject);
        return subject;
    }

    public AcademicOfferingCycle programCycle(Program program, Term term, AcademicOfferingCycleStatus status) {
        AcademicOfferingCycle cycle = new AcademicOfferingCycle();
        cycle.setProgram(program);
        cycle.setTerm(term);
        cycle.setStatus(status);
        cycle.setCreatedBy(person());
        cycle.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        entityManager.persist(cycle);
        return cycle;
    }

    public SubjectSection section(Subject subject, Term term, StatusType status, AcademicOfferingCycle cycle) {
        SubjectSection section = new SubjectSection();
        section.setName("Comisión " + UUID.randomUUID());
        section.setSubject(subject);
        section.setTerm(term);
        section.setCapacity(20);
        section.setStatus(status);
        section.setAcademicOfferingCycle(cycle);
        entityManager.persist(section);
        return section;
    }

    public void assignLead(SubjectSection section, Instructor instructor) {
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
    }

    public SectionScheduleSlot slot(SubjectSection section, Classroom classroom, DayOfWeek day, LocalTime start, LocalTime end) {
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setSubjectSection(section);
        slot.setClassroom(classroom);
        slot.setDayOfWeek(day);
        slot.setStartTime(start);
        slot.setEndTime(end);
        entityManager.persist(slot);
        return slot;
    }

    public void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
