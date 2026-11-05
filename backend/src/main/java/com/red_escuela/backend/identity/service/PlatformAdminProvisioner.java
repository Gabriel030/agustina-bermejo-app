package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.RoleNotFoundException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.platform-admin", name = {"username", "email", "password"})
public class PlatformAdminProvisioner implements ApplicationRunner {

    public static final String ROLE_NAME = "PLATFORM_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminProvisioner.class);

    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final PersonFactory personFactory;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public PlatformAdminProvisioner(
            PersonRepository personRepository,
            RoleRepository roleRepository,
            PersonFactory personFactory,
            PasswordPolicyValidator passwordPolicyValidator,
            PasswordEncoder passwordEncoder,
            @Value("${app.platform-admin.username}") String username,
            @Value("${app.platform-admin.email}") String email,
            @Value("${app.platform-admin.password}") String password) {
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.personFactory = personFactory;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (personRepository.existsByRoles_Name(ROLE_NAME)) {
            return;
        }
        passwordPolicyValidator.validate(password);
        Person person = personFactory.createBasePerson("Plataforma", "Admin", email);
        person.setUsername(username);
        person.setPassword(passwordEncoder.encode(password));
        person.setDni("PLATFORM-ADMIN");
        Role role = roleRepository.findByName(ROLE_NAME).orElseThrow(() -> new RoleNotFoundException(ROLE_NAME));
        person.getRoles().add(role);
        personRepository.save(person);
        log.info("platform_admin_provisioned username={}", username);
    }
}
