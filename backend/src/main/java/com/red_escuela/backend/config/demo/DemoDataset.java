package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DemoDataset {

    private Institution institution;
    private Person director;
    private Person headOfStudies;

    private final Map<Integer, Term> terms = new LinkedHashMap<>();
    private final List<Person> administrativeStaff = new ArrayList<>();
    private final List<Person> preceptors = new ArrayList<>();
    private final List<Instructor> instructors = new ArrayList<>();
    private final Map<String, Subject> subjects = new LinkedHashMap<>();
    private final Map<String, Program> programs = new LinkedHashMap<>();
    private final Map<String, ProgramSubject> programSubjects = new LinkedHashMap<>();
    private final Map<String, SubjectSection> sections = new LinkedHashMap<>();
    private final Map<String, Course> courses = new LinkedHashMap<>();
    private final Map<String, CourseSubject> courseSubjects = new LinkedHashMap<>();
    private final Map<String, CourseSubjectSection> courseSections = new LinkedHashMap<>();
    private final Map<String, EvaluationScheme> evaluationSchemes = new LinkedHashMap<>();
    private final List<Classroom> classrooms = new ArrayList<>();
    private final Map<String, AcademicOfferingCycle> offeringCycles = new LinkedHashMap<>();
    private final Map<Long, Instructor> sectionInstructors = new LinkedHashMap<>();
    private final Map<Long, Instructor> courseSectionInstructors = new LinkedHashMap<>();
    private final List<Student> students = new ArrayList<>();
    private final List<ProgramEnrollment> programEnrollments = new ArrayList<>();
    private final List<CourseEnrollment> courseEnrollments = new ArrayList<>();
    private final List<SubjectSectionEnrollment> sectionEnrollments = new ArrayList<>();
    private final List<CourseSubjectSectionEnrollment> courseSectionEnrollments = new ArrayList<>();

    private Student demoStudent;
    private Instructor demoInstructor;

    public Term term(int year) {
        return terms.get(year);
    }

    public Program program(String name) {
        return programs.get(name);
    }

    public Subject subject(String name) {
        return subjects.get(name);
    }

    public ProgramSubject programSubject(String programName, String subjectName) {
        return programSubjects.get(programName + "|" + subjectName);
    }

    public SubjectSection section(String programName, String subjectName, int year) {
        return sections.get(programName + "|" + subjectName + "|" + year);
    }

    public Course course(String name) {
        return courses.get(name);
    }

    public CourseSubjectSection courseSection(String courseName, String subjectName) {
        return courseSections.get(courseName + "|" + subjectName);
    }

    public Classroom classroomAt(int index) {
        return classrooms.get(index % classrooms.size());
    }

    public AcademicOfferingCycle offeringCycle(String programName, int year) {
        return offeringCycles.get(programName + "|" + year);
    }

    public Instructor instructorAt(int index) {
        return instructors.get(index % instructors.size());
    }

    public Person administrativeAt(int index) {
        return administrativeStaff.get(index % administrativeStaff.size());
    }

    public Person preceptorAt(int index) {
        return preceptors.get(index % preceptors.size());
    }
}
