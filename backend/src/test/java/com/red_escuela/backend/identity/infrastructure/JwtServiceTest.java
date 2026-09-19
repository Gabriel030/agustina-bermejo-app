package com.red_escuela.backend.identity.infrastructure;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-does-not-need-to-be-32-bytes";

    private Person personWithRoles(String... roleNames) {
        Person person = new Person();
        person.setId(42L);
        Set<Role> roles = new java.util.HashSet<>();
        for (String roleName : roleNames) {
            Role role = new Role();
            role.setName(roleName);
            roles.add(role);
        }
        person.setRoles(roles);
        return person;
    }

    @Test
    void generatesAndParsesAValidTokenWithTheSameSubAndRoles() {
        JwtService jwtService = new JwtService(SECRET, 15);
        Person person = personWithRoles("DOCENTE", "PRECEPTOR");

        String token = jwtService.generateAccessToken(person);
        Claims claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("roles", List.class)).containsExactlyInAnyOrder("DOCENTE", "PRECEPTOR");
    }

    @Test
    void expiredTokenFailsValidation() {
        JwtService expiredService = new JwtService(SECRET, -1);
        String token = expiredService.generateAccessToken(personWithRoles("ALUMNO"));

        assertThatThrownBy(() -> expiredService.validateAndParse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretFailsValidation() {
        JwtService serviceA = new JwtService(SECRET, 15);
        JwtService serviceB = new JwtService("otro-secret-completamente-distinto", 15);

        String token = serviceA.generateAccessToken(personWithRoles("ALUMNO"));

        assertThatThrownBy(() -> serviceB.validateAndParse(token))
                .isInstanceOf(SignatureException.class);
    }
}
