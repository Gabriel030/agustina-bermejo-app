package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.service.PersonFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
@Profile("local & demo")
public class DemoPeopleSeeder {

    private static final String DEMO_PASSWORD = "Test1234";

    private static final List<String[]> TEACHERS = List.of(
            new String[] {"Mariana", "Gómez"},
            new String[] {"Rodrigo", "Paz"},
            new String[] {"Silvia", "Ocampo"},
            new String[] {"Javier", "Medina"},
            new String[] {"Carolina", "Ruiz"},
            new String[] {"Esteban", "Quiroga"},
            new String[] {"Laura", "Benítez"},
            new String[] {"Diego", "Sosa"},
            new String[] {"Verónica", "Lagos"},
            new String[] {"Martín", "Cabrera"},
            new String[] {"Patricia", "Álvarez"},
            new String[] {"Hernán", "Figueroa"});

    private static final List<String[]> PRECEPTORS = List.of(
            new String[] {"Natalia", "Ibarra"},
            new String[] {"Gustavo", "Peralta"},
            new String[] {"Romina", "Vega"});

    private static final List<String[]> ADMINISTRATIVES = List.of(
            new String[] {"Cecilia", "Moyano"},
            new String[] {"Pablo", "Duarte"},
            new String[] {"Andrea", "Ferrari"});

    private static final List<String[]> STUDENTS = List.of(
            new String[] {"Martina", "Acosta"}, new String[] {"Tomás", "Aguirre"},
            new String[] {"Valentina", "Barrios"}, new String[] {"Joaquín", "Bustos"},
            new String[] {"Camila", "Cáceres"}, new String[] {"Lautaro", "Carrizo"},
            new String[] {"Sofía", "Castro"}, new String[] {"Benjamín", "Coronel"},
            new String[] {"Julieta", "Díaz"}, new String[] {"Mateo", "Domínguez"},
            new String[] {"Agustina", "Escobar"}, new String[] {"Thiago", "Farías"},
            new String[] {"Delfina", "Flores"}, new String[] {"Bautista", "Franco"},
            new String[] {"Emilia", "Giménez"}, new String[] {"Santino", "Godoy"},
            new String[] {"Catalina", "Herrera"}, new String[] {"Lucas", "Juárez"},
            new String[] {"Mía", "Ledesma"}, new String[] {"Ignacio", "Leiva"},
            new String[] {"Renata", "Luna"}, new String[] {"Gael", "Maidana"},
            new String[] {"Isabella", "Molina"}, new String[] {"Lorenzo", "Nieva"},
            new String[] {"Victoria", "Olivera"}, new String[] {"Felipe", "Ponce"},
            new String[] {"Zoe", "Ramírez"}, new String[] {"Dante", "Rivero"},
            new String[] {"Abril", "Rodríguez"}, new String[] {"Simón", "Romero"},
            new String[] {"Pilar", "Salas"}, new String[] {"Bruno", "Sánchez"},
            new String[] {"Guadalupe", "Suárez"}, new String[] {"Ciro", "Toledo"},
            new String[] {"Alma", "Torres"}, new String[] {"Facundo", "Vargas"},
            new String[] {"Jazmín", "Velázquez"}, new String[] {"Máximo", "Villalba"},
            new String[] {"Antonella", "Zalazar"}, new String[] {"Santiago", "Zarate"});

    private final InstitutionRepository institutionRepository;
    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final InstructorRepository instructorRepository;
    private final StudentRepository studentRepository;
    private final PersonFactory personFactory;
    private final PasswordEncoder passwordEncoder;

    public DemoPeopleSeeder(
            InstitutionRepository institutionRepository,
            PersonRepository personRepository,
            RoleRepository roleRepository,
            InstructorRepository instructorRepository,
            StudentRepository studentRepository,
            PersonFactory personFactory,
            PasswordEncoder passwordEncoder) {
        this.institutionRepository = institutionRepository;
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.instructorRepository = instructorRepository;
        this.studentRepository = studentRepository;
        this.personFactory = personFactory;
        this.passwordEncoder = passwordEncoder;
    }

    public void seed(DemoDataset dataset) {
        dataset.setInstitution(createInstitution());
        createDirectors(dataset);
        createAdministratives(dataset);
        createPreceptors(dataset);
        createTeachers(dataset);
        createStudents(dataset);
    }

    private Institution createInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto Superior Agustina Bermejo");
        institution.setEmail("secretaria@isab.edu.ar");
        institution.setPhoneNumber("0351 422-7788");
        institution.setWebsite("https://www.isab.edu.ar");
        institution.setPrefNotas(true);
        institution.setPrefCalendario(true);
        institution.setPrefAsignarCurso(true);
        return institutionRepository.save(institution);
    }

    private void createDirectors(DemoDataset dataset) {
        Person director = resolveDemoAccount("director.demo", "Agustina", "Bermejo", "DIRECTOR", 1);
        dataset.setDirector(director);
        dataset.setHeadOfStudies(createStaff("Marcela", "Ríos", "DIRECTOR", 1));
    }

    private void createAdministratives(DemoDataset dataset) {
        dataset.getAdministrativeStaff().add(
                resolveDemoAccount("administrativo.demo", "Cecilia", "Moyano", "ADMINISTRATIVO", 2));
        for (int i = 1; i < ADMINISTRATIVES.size(); i++) {
            String[] name = ADMINISTRATIVES.get(i);
            dataset.getAdministrativeStaff().add(createStaff(name[0], name[1], "ADMINISTRATIVO", 10 + i));
        }
    }

    private void createPreceptors(DemoDataset dataset) {
        dataset.getPreceptors().add(resolveDemoAccount("preceptor.demo", "Natalia", "Ibarra", "PRECEPTOR", 3));
        for (int i = 1; i < PRECEPTORS.size(); i++) {
            String[] name = PRECEPTORS.get(i);
            dataset.getPreceptors().add(createStaff(name[0], name[1], "PRECEPTOR", 20 + i));
        }
    }

    private void createTeachers(DemoDataset dataset) {
        Person demoTeacher = resolveDemoAccount("docente.demo", "Mariana", "Gómez", "DOCENTE", 4);
        Instructor demoInstructor = createInstructor(demoTeacher);
        dataset.setDemoInstructor(demoInstructor);
        dataset.getInstructors().add(demoInstructor);
        for (int i = 1; i < TEACHERS.size(); i++) {
            String[] name = TEACHERS.get(i);
            Person person = createStaff(name[0], name[1], "DOCENTE", 30 + i);
            dataset.getInstructors().add(createInstructor(person));
        }
    }

    private void createStudents(DemoDataset dataset) {
        Person demoPerson = resolveDemoAccount("alumno.demo", "Lucía", "Fernández", "ALUMNO", 5);
        Student demoStudent = createStudent(demoPerson, dataset.getInstitution());
        dataset.setDemoStudent(demoStudent);
        dataset.getStudents().add(demoStudent);
        for (int i = 0; i < STUDENTS.size(); i++) {
            String[] name = STUDENTS.get(i);
            Person person = createPerson(name[0], name[1], "ALUMNO", 100 + i);
            dataset.getStudents().add(createStudent(person, dataset.getInstitution()));
        }
    }

    private Person resolveDemoAccount(
            String username, String firstName, String lastName, String roleName, int sequence) {
        Person person = personRepository.findByUsername(username).orElseGet(() -> {
            Person created = personFactory.createBasePerson(firstName, lastName, username + "@isab.edu.ar");
            created.setUsername(username);
            created.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
            created.setDni("DEMO-" + username);
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Falta el rol " + roleName));
            created.getRoles().add(role);
            return created;
        });
        person.setName(firstName);
        person.setLastname(lastName);
        person.setStatus(StatusType.ACTIVE);
        person.setGrantSystemAccess(true);
        person.setEmailVerified(true);
        person.setPhoneNumber("351 44" + String.format("%01d", sequence) + "-" + String.format("%04d", 2000 + sequence));
        return personRepository.save(person);
    }

    private Person createStaff(String firstName, String lastName, String roleName, int sequence) {
        return createPerson(firstName, lastName, roleName, sequence);
    }

    private Person createPerson(String firstName, String lastName, String roleName, int sequence) {
        String username = slug(firstName) + "." + slug(lastName);
        Person person = personFactory.createBasePerson(firstName, lastName, username + "@isab.edu.ar");
        person.setUsername(username);
        person.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        person.setDni(String.valueOf(28000000 + sequence * 137));
        person.setStatus(StatusType.ACTIVE);
        person.setGrantSystemAccess(true);
        person.setEmailVerified(true);
        person.setPhoneNumber("351 5" + String.format("%02d", sequence % 100) + "-" + String.format("%04d", 1000 + sequence));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + roleName));
        person.getRoles().add(role);
        return personRepository.save(person);
    }

    private Instructor createInstructor(Person person) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        return instructorRepository.save(instructor);
    }

    private Student createStudent(Person person, Institution institution) {
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student.setGrantFinalExamSignUp(true);
        return studentRepository.save(student);
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
}
