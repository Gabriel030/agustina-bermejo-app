package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.InstructorProfileCreationPort;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("local & demo")
@Order(DemoLearnerAccountsSeeder.RUN_ORDER)
public class DemoLearnerAccountsSeeder implements ApplicationRunner {
    public static final int RUN_ORDER = 10;
    private static final String DEMO_PASSWORD = "Test1234";
    private static final String STUDENT_ROLE = "ALUMNO";
    private static final String TEACHER_USERNAME = "docente.demo";

    private record LearnerAccount(String username, String firstName, String lastName) {}

    private static final List<LearnerAccount> LEARNERS = List.of(
            new LearnerAccount("alumno.demo", "Alumno", "Demo"),
            new LearnerAccount("alumno.egreso.demo", "Alumno Egreso", "Demo"),
            new LearnerAccount("alumno.cursos.demo", "Alumno Cursos", "Demo"),
            new LearnerAccount("alumno.inscripcion.demo", "Alumno Inscripción", "Demo"),
            new LearnerAccount("alumno.cupo.demo", "Alumno Cupo", "Demo"),
            new LearnerAccount("alumno.notas.demo", "Alumno Notas", "Demo"));

    private final LocalSeedRunner demoAccountsSeed;
    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final PersonFactory personFactory;
    private final PasswordEncoder passwordEncoder;
    private final InstitutionRepository institutionRepository;
    private final StudentRepository studentRepository;
    private final InstructorProfileCreationPort instructorProfileCreationPort;

    public DemoLearnerAccountsSeeder(
            LocalSeedRunner demoAccountsSeed,
            PersonRepository personRepository,
            RoleRepository roleRepository,
            PersonFactory personFactory,
            PasswordEncoder passwordEncoder,
            InstitutionRepository institutionRepository,
            StudentRepository studentRepository,
            InstructorProfileCreationPort instructorProfileCreationPort) {
        this.demoAccountsSeed = demoAccountsSeed;
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.personFactory = personFactory;
        this.passwordEncoder = passwordEncoder;
        this.institutionRepository = institutionRepository;
        this.studentRepository = studentRepository;
        this.instructorProfileCreationPort = instructorProfileCreationPort;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        demoAccountsSeed.run(args);
        Institution institution = findOrCreateInstitution();
        LEARNERS.forEach(learner -> seedConfirmedStudent(learner, institution));
        personRepository.findByUsername(TEACHER_USERNAME).ifPresent(instructorProfileCreationPort::createInstructorProfile);
    }

    private Institution findOrCreateInstitution() {
        return institutionRepository.findAll().stream().findFirst().orElseGet(() -> {
            Institution institution = new Institution();
            institution.setName("Instituto Demo");
            return institutionRepository.save(institution);
        });
    }

    private void seedConfirmedStudent(LearnerAccount learner, Institution institution) {
        Person person = personRepository.findByUsername(learner.username()).orElseGet(() -> createLearnerPerson(learner));
        if (studentRepository.findByPerson_Id(person.getId()).isPresent()) {
            return;
        }
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        studentRepository.save(student);
    }

    private Person createLearnerPerson(LearnerAccount learner) {
        Person person = personFactory.createBasePerson(learner.firstName(), learner.lastName(), learner.username() + "@demo.local");
        person.setUsername(learner.username());
        person.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        person.setDni("DEMO-" + learner.username());
        Role role = roleRepository.findByName(STUDENT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + STUDENT_ROLE));
        person.getRoles().add(role);
        return personRepository.save(person);
    }
}
