package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.RequirementType;
import com.red_escuela.backend.academicstructure.entity.GradeMode;
import com.red_escuela.backend.academicstructure.entity.ProgramLevel;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.config.demo.DemoCatalog.CourseSpec;
import com.red_escuela.backend.config.demo.DemoCatalog.ProgramSpec;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.repository.EvaluationSchemeRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
@Profile("local & demo")
public class DemoAcademicOfferSeeder {

    private static final List<Integer> HISTORY_YEARS = List.of(2023, 2024, 2025, 2026);
    private static final List<String> SCHEDULES = List.of(
            "Lunes y miércoles de 18:00 a 20:00 — Aula 12",
            "Martes y jueves de 18:00 a 20:00 — Aula 7",
            "Lunes y viernes de 20:00 a 22:00 — Aula 3",
            "Miércoles y viernes de 18:30 a 20:30 — Aula 15",
            "Sábados de 9:00 a 13:00 — Aula Magna",
            "Martes y jueves de 20:00 a 22:00 — Laboratorio 2");

    private static final String SCHEME_PROMOTIONAL = "Promocional — 2 parciales y 3 TP";
    private static final String SCHEME_FINAL = "Con final obligatorio";
    private static final String SCHEME_PRACTICE = "Práctica supervisada";

    private final TermRepository termRepository;
    private final SubjectRepository subjectRepository;
    private final ProgramRepository programRepository;
    private final ProgramSubjectRepository programSubjectRepository;
    private final ProgramSubjectPrerequisiteRepository prerequisiteRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseRepository courseRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;
    private final EvaluationSchemeRepository evaluationSchemeRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionScheduleSlotRepository scheduleSlotRepository;
    private final AcademicOfferingCycleRepository offeringCycleRepository;

    private int subjectCodeSequence = 1;
    private int instructorRotation = 0;

    public DemoAcademicOfferSeeder(
            TermRepository termRepository,
            SubjectRepository subjectRepository,
            ProgramRepository programRepository,
            ProgramSubjectRepository programSubjectRepository,
            ProgramSubjectPrerequisiteRepository prerequisiteRepository,
            SubjectSectionRepository subjectSectionRepository,
            SubjectSectionInstructorRepository subjectSectionInstructorRepository,
            CourseRepository courseRepository,
            CourseSubjectRepository courseSubjectRepository,
            CourseSubjectSectionRepository courseSubjectSectionRepository,
            CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
            EvaluationSchemeRepository evaluationSchemeRepository,
            ClassroomRepository classroomRepository,
            SectionScheduleSlotRepository scheduleSlotRepository,
            AcademicOfferingCycleRepository offeringCycleRepository) {
        this.termRepository = termRepository;
        this.subjectRepository = subjectRepository;
        this.programRepository = programRepository;
        this.programSubjectRepository = programSubjectRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseRepository = courseRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.evaluationSchemeRepository = evaluationSchemeRepository;
        this.classroomRepository = classroomRepository;
        this.scheduleSlotRepository = scheduleSlotRepository;
        this.offeringCycleRepository = offeringCycleRepository;
    }

    public void seed(DemoDataset dataset) {
        createTerms(dataset);
        createClassrooms(dataset);
        createEvaluationSchemes(dataset);
        createPrograms(dataset);
        createPrerequisites(dataset);
        createOfferingCycles(dataset);
        createProgramSections(dataset);
        createCourses(dataset);
        createProposedSubjects(dataset);
    }

    private void createProposedSubjects(DemoDataset dataset) {
        List<String[]> proposals = List.of(
                new String[] {"Educación Inclusiva", "PENDING_APPROVAL"},
                new String[] {"Neurociencias y Aprendizaje", "PENDING_APPROVAL"},
                new String[] {"Programación Móvil", "PENDING_APPROVAL"},
                new String[] {"Inteligencia Artificial Aplicada", "PENDING_APPROVAL"},
                new String[] {"Robótica Educativa", "DRAFT"},
                new String[] {"Economía Circular", "DRAFT"},
                new String[] {"Gestión de Datos Clínicos", "REJECTED"});
        for (String[] proposal : proposals) {
            Subject subject = new Subject();
            subject.setName(proposal[0]);
            subject.setCode(String.format("S%03d", subjectCodeSequence++));
            subject.setStatus(SubjectStatus.valueOf(proposal[1]));
            subject.setDescription("Propuesta de materia nueva para el plan de estudios.");
            subject.setMaxFinalExamAttempts(1);
            subject.setWeeklyHours(4);
            subject.setWeeklyHoursLab(0);
            subject.setGradeMode(GradeMode.NUMERIC);
            subjectRepository.save(subject);
        }
    }

    private void createClassrooms(DemoDataset dataset) {
        for (String[] spec : DemoCatalog.CLASSROOMS) {
            Classroom classroom = new Classroom();
            classroom.setName(spec[0]);
            classroom.setCapacity(Integer.parseInt(spec[1]));
            classroom.setStatus(StatusType.ACTIVE);
            dataset.getClassrooms().add(classroomRepository.save(classroom));
        }
    }

    private void createOfferingCycles(DemoDataset dataset) {
        for (ProgramSpec spec : DemoCatalog.PROGRAMS) {
            boolean withHistory = spec.name().equals(DemoCatalog.EARLY_CHILDHOOD)
                    || spec.name().equals(DemoCatalog.SYSTEMS_ANALYSIS);
            List<Integer> years = withHistory ? HISTORY_YEARS : List.of(2026);
            for (Integer year : years) {
                dataset.getOfferingCycles().put(spec.name() + "|" + year,
                        saveCycle(dataset, spec.name(), year, AcademicOfferingCycleStatus.APPROVED));
            }
        }
        saveCycle(dataset, DemoCatalog.EARLY_CHILDHOOD, 2027, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        saveCycle(dataset, DemoCatalog.SYSTEMS_ANALYSIS, 2027, AcademicOfferingCycleStatus.PENDING_APPROVAL);
        saveCycle(dataset, DemoCatalog.NURSING, 2027, AcademicOfferingCycleStatus.DRAFT);
        saveCycle(dataset, DemoCatalog.ENVIRONMENTAL, 2027, AcademicOfferingCycleStatus.REJECTED);
    }

    private AcademicOfferingCycle saveCycle(
            DemoDataset dataset, String programName, int year, AcademicOfferingCycleStatus status) {
        AcademicOfferingCycle cycle = new AcademicOfferingCycle();
        cycle.setTerm(dataset.term(year));
        cycle.setProgram(dataset.program(programName));
        cycle.setStatus(status);
        cycle.setCreatedBy(dataset.preceptorAt(0));
        cycle.setCreatedAt(LocalDate.of(year - 1, 11, 10).atTime(10, 0));
        if (status == AcademicOfferingCycleStatus.APPROVED || status == AcademicOfferingCycleStatus.REJECTED) {
            cycle.setDecidedBy(dataset.getDirector());
            cycle.setDecidedAt(LocalDate.of(year - 1, 11, 20).atTime(15, 30));
        }
        if (status == AcademicOfferingCycleStatus.REJECTED) {
            cycle.setRejectionReason("Faltan docentes asignados en tres comisiones de primer año");
        }
        return offeringCycleRepository.save(cycle);
    }

    private void createTerms(DemoDataset dataset) {
        dataset.getTerms().put(2023, saveTerm(2023, "2023-03-06", "2023-12-15", "2023-01-09", "2023-03-31"));
        dataset.getTerms().put(2024, saveTerm(2024, "2024-03-04", "2024-12-13", "2024-01-08", "2024-03-29"));
        dataset.getTerms().put(2025, saveTerm(2025, "2025-03-03", "2025-12-12", "2025-01-06", "2025-03-28"));
        dataset.getTerms().put(2026, saveTerm(2026, "2026-03-02", "2026-12-11", "2026-01-05", "2026-03-31"));
        dataset.getTerms().put(2027, saveTerm(2027, "2027-03-01", "2027-12-10", "2026-10-01", "2027-02-26"));
    }

    private Term saveTerm(int year, String start, String end, String enrollmentStart, String enrollmentEnd) {
        Term term = new Term();
        term.setYear(year);
        term.setDescription("Ciclo lectivo " + year);
        term.setType("ANNUAL");
        term.setStartDate(LocalDate.parse(start).atStartOfDay());
        term.setEndDate(LocalDate.parse(end).atTime(23, 59));
        term.setEnrollmentStart(LocalDate.parse(enrollmentStart).atStartOfDay());
        term.setEnrollmentEnd(LocalDate.parse(enrollmentEnd).atTime(23, 59));
        term.setStatus(StatusType.ACTIVE);
        return termRepository.save(term);
    }

    private void createEvaluationSchemes(DemoDataset dataset) {
        dataset.getEvaluationSchemes().put(SCHEME_PROMOTIONAL,
                saveScheme(SCHEME_PROMOTIONAL, 2, 3, false, null, GradeAggregationMethod.SIMPLE_AVERAGE, 4.0, 6.0, 7.0,
                        "Promociona con 7 o más en ambos parciales y los tres trabajos prácticos aprobados."));
        dataset.getEvaluationSchemes().put(SCHEME_FINAL,
                saveScheme(SCHEME_FINAL, 2, 2, true, 0.4, GradeAggregationMethod.WEIGHTED_AVERAGE, 4.0, 4.0, null,
                        "Regulariza con 4 o más y rinde final en mesa."));
        dataset.getEvaluationSchemes().put(SCHEME_PRACTICE,
                saveScheme(SCHEME_PRACTICE, 1, 3, true, 0.3, GradeAggregationMethod.SIMPLE_AVERAGE, 6.0, 6.0, null,
                        "Requiere 80% de asistencia a la práctica y coloquio final."));
    }

    private static com.red_escuela.backend.shared.domain.Grade grade(Double value) {
        return value == null ? null : com.red_escuela.backend.shared.domain.Grade.of(java.math.BigDecimal.valueOf(value));
    }

    private EvaluationScheme saveScheme(
            String name, int exams, int tps, boolean hasFinal, Double finalWeight,
            GradeAggregationMethod method, Double minRegular, Double minApproval, Double promotion, String notes) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName(name);
        scheme.setExamCountExpected(exams);
        scheme.setTpCountExpected(tps);
        scheme.setHasFinal(hasFinal);
        scheme.setFinalWeight(finalWeight);
        scheme.setAggregationMethod(method);
        scheme.setMinRegularGrade(grade(minRegular));
        scheme.setMinApprovalGrade(grade(minApproval));
        scheme.setPromotionMinGrade(grade(promotion));
        scheme.setNotes(notes);
        return evaluationSchemeRepository.save(scheme);
    }

    private void createPrograms(DemoDataset dataset) {
        for (ProgramSpec spec : DemoCatalog.PROGRAMS) {
            Program program = new Program();
            program.setName(spec.name());
            program.setStatus(StatusType.ACTIVE);
            program.setPrice(spec.monthlyPrice());
            program.setEnrollmentFeeAmount(spec.enrollmentFee());
            program.setTotalYears(spec.totalYears());
            program.setTotalCreditsRequired(spec.totalYears() * 6 * 8);
            program.setTotalHoursRequired(spec.totalYears() * 6 * 128);
            program.setVisibleInCatalog(true);
            program.setTeachingMode(spec.name().equals(DemoCatalog.EDUCATION_MANAGEMENT)
                    ? TeachingMode.HYBRID
                    : TeachingMode.IN_PERSON);
            program.setLevel(ProgramLevel.UNDERGRADUATE);
            program.setGraduationProfile("El egresado está capacitado para desempeñarse en instituciones "
                    + "públicas y privadas, con formación teórica y práctica supervisada.");
            program.setAdmissionRequirements("Título secundario completo, DNI y certificado de aptitud psicofísica.");
            Program saved = programRepository.save(program);
            dataset.getPrograms().put(spec.name(), saved);

            for (int yearIndex = 0; yearIndex < spec.subjectsByYear().size(); yearIndex++) {
                List<String> subjectNames = spec.subjectsByYear().get(yearIndex);
                for (int order = 0; order < subjectNames.size(); order++) {
                    String subjectName = subjectNames.get(order);
                    Subject subject = resolveSubject(dataset, subjectName);
                    ProgramSubject programSubject = new ProgramSubject();
                    programSubject.setProgram(saved);
                    programSubject.setSubject(subject);
                    programSubject.setCredits(8);
                    programSubject.setCurricularYear(yearIndex + 1);
                    programSubject.setCurricularTerm(CurricularTerm.ANNUAL);
                    programSubject.setSortOrder(order + 1);
                    programSubject.setStatus(StatusType.ACTIVE);
                    programSubject.setSubjectRecurrence(
                            subjectName.startsWith("Práctica") ? SubjectRecurrence.ANNUAL : SubjectRecurrence.PROMOTIONAL);
                    dataset.getProgramSubjects().put(
                            spec.name() + "|" + subjectName, programSubjectRepository.save(programSubject));
                }
            }
        }
    }

    private Subject resolveSubject(DemoDataset dataset, String name) {
        Subject existing = dataset.getSubjects().get(name);
        if (existing != null) {
            return existing;
        }
        Subject subject = new Subject();
        subject.setName(name);
        subject.setCode(String.format("S%03d", subjectCodeSequence++));
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setMaxFinalExamAttempts(1);
        subject.setWeeklyHours(4);
        subject.setWeeklyHoursLab(name.startsWith("Práctica") ? 4 : 0);
        subject.setGradeMode(name.startsWith("Práctica") ? GradeMode.PASS_FAIL : GradeMode.NUMERIC);
        Subject saved = subjectRepository.save(subject);
        dataset.getSubjects().put(name, saved);
        return saved;
    }

    private void createPrerequisites(DemoDataset dataset) {
        for (ProgramSpec spec : DemoCatalog.PROGRAMS) {
            for (List<String> yearSubjects : spec.subjectsByYear()) {
                for (String subjectName : yearSubjects) {
                    String requiredName = DemoCatalog.prerequisiteOf(subjectName);
                    if (requiredName == null) {
                        continue;
                    }
                    ProgramSubject target = dataset.programSubject(spec.name(), subjectName);
                    ProgramSubject required = dataset.programSubject(spec.name(), requiredName);
                    if (target == null || required == null) {
                        continue;
                    }
                    ProgramSubjectPrerequisite prerequisite = new ProgramSubjectPrerequisite();
                    prerequisite.setProgramSubject(target);
                    prerequisite.setPrerequisiteProgramSubject(required);
                    prerequisite.setRequirementType(RequirementType.APPROVED);
                    prerequisite.setNotes("Correlativa de cursado y final");
                    prerequisiteRepository.save(prerequisite);
                }
            }
        }
    }

    private void createProgramSections(DemoDataset dataset) {
        for (ProgramSpec spec : DemoCatalog.PROGRAMS) {
            boolean withHistory = spec.name().equals(DemoCatalog.EARLY_CHILDHOOD)
                    || spec.name().equals(DemoCatalog.SYSTEMS_ANALYSIS);
            List<Integer> years = withHistory ? HISTORY_YEARS : List.of(2026);
            for (Integer year : years) {
                Term term = dataset.term(year);
                for (List<String> yearSubjects : spec.subjectsByYear()) {
                    for (String subjectName : yearSubjects) {
                        createSection(dataset, spec, subjectName, term, year);
                    }
                }
            }
        }
    }

    private void createSection(DemoDataset dataset, ProgramSpec spec, String subjectName, Term term, int year) {
        SubjectSection section = new SubjectSection();
        section.setName(shortProgramCode(spec.name()) + " " + subjectName + " — Comisión A");
        section.setSubject(dataset.subject(subjectName));
        section.setTerm(term);
        section.setEvaluationScheme(dataset.getEvaluationSchemes().get(schemeNameFor(spec, subjectName)));
        section.setSchedule(SCHEDULES.get(instructorRotation % SCHEDULES.size()));
        section.setCapacity(35);
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(term.getStartDate().minusDays(30));
        section.setMaxAbsenceRate(com.red_escuela.backend.shared.domain.Percentage.ofPoints(java.math.BigDecimal.valueOf(25)));
        section.setAcademicOfferingCycle(dataset.offeringCycle(spec.name(), year));
        SubjectSection saved = subjectSectionRepository.save(section);
        dataset.getSections().put(spec.name() + "|" + subjectName + "|" + year, saved);
        saveScheduleSlot(dataset, saved, null);

        Instructor instructor = pickInstructor(dataset, spec, year);
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(saved);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        assignment.setStartAt(term.getStartDate());
        assignment.setHoursPerWeek(4);
        subjectSectionInstructorRepository.save(assignment);
        dataset.getSectionInstructors().put(saved.getId(), instructor);
        instructorRotation++;
    }

    private Instructor pickInstructor(DemoDataset dataset, ProgramSpec spec, int year) {
        boolean demoTeacherSlot = year == 2026
                && spec.name().equals(DemoCatalog.EARLY_CHILDHOOD)
                && instructorRotation % 3 == 0;
        return demoTeacherSlot ? dataset.getDemoInstructor() : dataset.instructorAt(instructorRotation);
    }

    private void saveScheduleSlot(DemoDataset dataset, SubjectSection subjectSection, CourseSubjectSection courseSection) {
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setSubjectSection(subjectSection);
        slot.setCourseSection(courseSection);
        slot.setClassroom(dataset.classroomAt(instructorRotation));
        slot.setDayOfWeek(DayOfWeek.of(1 + instructorRotation % 5));
        slot.setStartTime(LocalTime.of(18, 0));
        slot.setEndTime(LocalTime.of(20, 0));
        scheduleSlotRepository.save(slot);
    }

    private String schemeNameFor(ProgramSpec spec, String subjectName) {
        if (subjectName.startsWith("Práctica") || subjectName.startsWith("Taller")) {
            return SCHEME_PRACTICE;
        }
        int position = spec.subjectsByYear().stream()
                .filter(subjects -> subjects.contains(subjectName))
                .mapToInt(subjects -> subjects.indexOf(subjectName))
                .findFirst()
                .orElse(0);
        return position % 2 == 0 ? SCHEME_PROMOTIONAL : SCHEME_FINAL;
    }

    private String shortProgramCode(String programName) {
        if (programName.equals(DemoCatalog.EARLY_CHILDHOOD)) return "PEI";
        if (programName.equals(DemoCatalog.PRIMARY_TEACHING)) return "PEP";
        if (programName.equals(DemoCatalog.BUSINESS_ADMIN)) return "TAE";
        if (programName.equals(DemoCatalog.SYSTEMS_ANALYSIS)) return "TAS";
        if (programName.equals(DemoCatalog.NURSING)) return "TSE";
        if (programName.equals(DemoCatalog.ENVIRONMENTAL)) return "TGA";
        return "LGE";
    }

    private void createCourses(DemoDataset dataset) {
        for (CourseSpec spec : DemoCatalog.COURSES) {
            Term term = dataset.term(spec.termYear());
            Course course = new Course();
            course.setName(spec.name());
            course.setTerm(term);
            course.setSchedule(spec.schedule());
            course.setCapacity(spec.capacity());
            course.setIsActive(StatusType.ACTIVE);
            course.setPrice(spec.price());
            course.setDurationMonths(spec.durationMonths());
            course.setWaitlistDeadlineOffsetDays(15);
            course.setVisibleInCatalog(true);
            course.setTeachingMode(spec.teachingMode());
            if (spec.registrationOpen()) {
                course.setRegistrationStart(LocalDate.of(2026, 10, 1).atStartOfDay());
                course.setRegistrationEnd(term.getStartDate().minusDays(5));
            } else {
                course.setRegistrationStart(term.getEnrollmentStart());
                course.setRegistrationEnd(term.getEnrollmentEnd());
            }
            Course savedCourse = courseRepository.save(course);
            dataset.getCourses().put(spec.name(), savedCourse);

            for (int order = 0; order < spec.subjects().size(); order++) {
                String subjectName = spec.subjects().get(order);
                CourseSubject courseSubject = new CourseSubject();
                courseSubject.setCourse(savedCourse);
                courseSubject.setName(subjectName);
                courseSubject.setSubjectRecurrence(SubjectRecurrence.PROMOTIONAL);
                courseSubject.setCurricularYear(1);
                courseSubject.setCurricularTerm(CurricularTerm.ANNUAL);
                courseSubject.setSortOrder(order + 1);
                courseSubject.setWeeklyWorkload(4);
                courseSubject.setStatus(StatusType.ACTIVE);
                courseSubject.setQualificationType("NUMERICA");
                courseSubject.setMaxFinalExamAttempts(1);
                CourseSubject savedSubject = courseSubjectRepository.save(courseSubject);
                dataset.getCourseSubjects().put(spec.name() + "|" + subjectName, savedSubject);

                CourseSubjectSection section = new CourseSubjectSection();
                section.setName(subjectName + " — Comisión única");
                section.setCourseSubject(savedSubject);
                section.setTerm(term);
                section.setEvaluationScheme(dataset.getEvaluationSchemes().get(SCHEME_PROMOTIONAL));
                section.setSchedule(spec.schedule());
                section.setCapacity(spec.capacity());
                section.setStatus(StatusType.ACTIVE);
                section.setCreatedAt(term.getStartDate().minusDays(30));
                section.setMaxAbsenceRate(com.red_escuela.backend.shared.domain.Percentage.ofPoints(java.math.BigDecimal.valueOf(25)));
                CourseSubjectSection savedSection = courseSubjectSectionRepository.save(section);
                dataset.getCourseSections().put(spec.name() + "|" + subjectName, savedSection);
                saveScheduleSlot(dataset, null, savedSection);

                Instructor courseInstructor = dataset.instructorAt(instructorRotation++);
                CourseSubjectSectionInstructor assignment = new CourseSubjectSectionInstructor();
                assignment.setSection(savedSection);
                assignment.setInstructor(courseInstructor);
                assignment.setRole(InstructorRole.LEAD);
                assignment.setStartAt(term.getStartDate());
                assignment.setHoursPerWeek(4);
                courseSubjectSectionInstructorRepository.save(assignment);
                dataset.getCourseSectionInstructors().put(savedSection.getId(), courseInstructor);
            }
        }
    }
}
