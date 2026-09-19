package com.red_escuela.backend.config;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {
    private static final String SECRET = "unit-test-secret-does-not-need-to-be-32-bytes";

    private final JwtService jwtService = new JwtService(SECRET, 15);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Person personWithRoles(String... roleNames) {
        Person person = new Person();
        person.setId(7L);
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            Role role = new Role();
            role.setName(name);
            roles.add(role);
        }
        person.setRoles(roles);
        return person;
    }

    @Test
    void withValidTokenBuildsAuthenticationWithTheClaimRoles() throws Exception {
        Person person = personWithRoles("DOCENTE", "PRECEPTOR");
        String token = jwtService.generateAccessToken(person);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("7");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_DOCENTE", "ROLE_PRECEPTOR");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void withoutTokenDoesNotBuildAuthenticationAndTheRequestContinues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void withInvalidTokenDoesNotBuildAuthenticationAndTheRequestContinues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-invalido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
