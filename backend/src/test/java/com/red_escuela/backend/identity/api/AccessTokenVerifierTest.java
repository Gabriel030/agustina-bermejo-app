package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenVerifierTest {
    private static final String SECRET = "unit-test-secret-does-not-need-to-be-32-bytes";

    private final JwtService jwtService = new JwtService(SECRET, 15);
    private final AccessTokenVerifier verifier = new AccessTokenVerifier(jwtService);

    @Test
    void validTokenYieldsPrincipalWithPersonIdAndRoles() {
        Person person = new Person();
        person.setId(42L);
        Role role = new Role();
        role.setName("ADMIN");
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        person.setRoles(roles);

        Optional<AuthenticatedPrincipal> principal = verifier.verify(jwtService.generateAccessToken(person));

        assertThat(principal).contains(new AuthenticatedPrincipal("42", java.util.List.of("ADMIN")));
    }

    @Test
    void malformedTokenYieldsEmpty() {
        assertThat(verifier.verify("not-a-jwt")).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretYieldsEmpty() {
        JwtService other = new JwtService("another-secret-with-enough-length-0123456789-abcdef", 15);
        Person person = new Person();
        person.setId(1L);
        person.setRoles(new HashSet<>());

        assertThat(verifier.verify(other.generateAccessToken(person))).isEmpty();
    }
}
