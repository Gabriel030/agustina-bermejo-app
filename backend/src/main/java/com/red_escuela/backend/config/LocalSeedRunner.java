package com.red_escuela.backend.config;

import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.RoleRepository;
import com.red_escuela.backend.service.PersonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Solo perfil "local" — crea una Person con login ya funcional por cada rol,
 * para poder entrar al frontend sin pasar a mano por el flujo de
 * forgot-password/reset-password cada vez que se reinicia la base.
 *
 * A propósito NO usa PersonFactory.crearPersonBase tal cual (que deja
 * username/password en null, forzando el flujo real de primer ingreso) — acá
 * se setea usuario/contraseña directo porque el propósito es tener una cuenta
 * lista para loguearse ya, no reproducir el flujo de alta real. Nunca se
 * activa en test/prod: ver "spring.profiles.active" — este perfil no tiene
 * ninguna referencia en application.properties, se activa a mano
 * (SPRING_PROFILES_ACTIVE=local) solo para desarrollo local.
 *
 * Idempotente por registro (no por un contador global): cada cuenta demo se
 * crea solo si su username todavía no existe, así no pisa datos que ya
 * hayas cargado a mano en la misma base.
 *
 * Alcance deliberadamente chico: solo Person + Role, sin Student/Instructor
 * ni datos académicos (programas, cursos, inscripciones). Hoy el frontend
 * (Fase 1) solo necesita login + layout por rol — cuando las épicas de
 * negocio (2, 3, 4) empiecen a necesitar datos reales para probar sus
 * pantallas, este seeder es el lugar donde sumarlos, no antes.
 */
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
            Person person = personFactory.crearPersonBase(
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
