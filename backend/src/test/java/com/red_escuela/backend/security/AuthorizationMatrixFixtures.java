package com.red_escuela.backend.security;

import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumMap;
import java.util.Map;

final class AuthorizationMatrixFixtures {

    private final Map<MatrixRole, String> tokensByRole = new EnumMap<>(MatrixRole.class);

    AuthorizationMatrixFixtures(
            TransactionTemplate transactions,
            EntityManager entityManager,
            RoleRepository roleRepository,
            JwtService jwtService) {
        transactions.executeWithoutResult(status -> {
            for (MatrixRole matrixRole : MatrixRole.values()) {
                Person person = persistPersonWithRole(entityManager, roleRepository, matrixRole);
                tokensByRole.put(matrixRole, jwtService.generateAccessToken(person));
            }
        });
    }

    String tokenFor(MatrixRole role) {
        return tokensByRole.get(role);
    }

    private Person persistPersonWithRole(EntityManager entityManager, RoleRepository roleRepository, MatrixRole matrixRole) {
        String username = "matrix." + matrixRole.name().toLowerCase();
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setName("Matriz");
        person.setLastname(matrixRole.name());
        person.setUsername(username);
        person.setDni(String.valueOf(90_000_000 + matrixRole.ordinal()));
        person.setEmail(username + "@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(matrixRole.name()).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
