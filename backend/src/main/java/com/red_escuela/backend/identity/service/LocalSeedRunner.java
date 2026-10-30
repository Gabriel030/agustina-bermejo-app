package com.red_escuela.backend.identity.service;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;

import com.red_escuela.backend.identity.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("local")
public class LocalSeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalSeedRunner.class);
    private static final String PASSWORD_DEMO = "Test1234";

    private record CuentaDemo(String username, String nombre, String apellido, String rol) {}

    private static final List<CuentaDemo> CUENTAS = List.of(
            new CuentaDemo("alumno.demo", "Alumno", "Demo", "ALUMNO"),
            new CuentaDemo("docente.demo", "Docente", "Demo", "DOCENTE"),
            new CuentaDemo("preceptor.demo", "Preceptor", "Demo", "PRECEPTOR"),
            new CuentaDemo("administrativo.demo", "Administrativo", "Demo", "ADMINISTRATIVO"),
            new CuentaDemo("director.demo", "Director", "Demo", "DIRECTOR"));

    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final PersonFactory personFactory;
    private final PasswordEncoder passwordEncoder;

    public LocalSeedRunner(
            PersonRepository personRepository,
            RoleRepository roleRepository,
            PersonFactory personFactory,
            PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.personFactory = personFactory;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (CuentaDemo cuenta : CUENTAS) {
            if (personRepository.findByUsername(cuenta.username()).isPresent()) {
                continue;
            }
            Person person = personFactory.createBasePerson(
                    cuenta.nombre(), cuenta.apellido(), cuenta.username() + "@demo.local");
            person.setUsername(cuenta.username());
            person.setPassword(passwordEncoder.encode(PASSWORD_DEMO));
            person.setDni("DEMO-" + cuenta.username());
            Role role = roleRepository.findByName(cuenta.rol())
                    .orElseThrow(() -> new IllegalStateException("Falta el rol " + cuenta.rol() + " (migración de roles no corrió)"));
            person.getRoles().add(role);
            personRepository.save(person);
            log.info("Seed local: cuenta demo creada — username={}, password={}, rol={}",
                    cuenta.username(), PASSWORD_DEMO, cuenta.rol());
        }
    }
}
