package com.red_escuela.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.util.StringUtils;

@Configuration
public class ActuatorSecurityConfig {

    static final String ACTUATOR_ROLE = "ACTUATOR";
    static final String ACTUATOR_REALM = "actuator";

    private final String actuatorUser;
    private final String actuatorPassword;

    public ActuatorSecurityConfig(@Value("${management.security.user:}") String actuatorUser,
                                  @Value("${management.security.password:}") String actuatorPassword) {
        this.actuatorUser = actuatorUser;
        this.actuatorPassword = actuatorPassword;
    }

    @Bean
    public UserDetailsService actuatorUserDetailsService(PasswordEncoder passwordEncoder) {
        if (!StringUtils.hasText(actuatorUser) || !StringUtils.hasText(actuatorPassword)) {
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(User.withUsername(actuatorUser)
                .password(passwordEncoder.encode(actuatorPassword))
                .roles(ACTUATOR_ROLE)
                .build());
    }

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                .anyRequest().hasRole(ACTUATOR_ROLE))
            .httpBasic(basic -> basic.authenticationEntryPoint(basicAuthenticationEntryPoint()))
            .exceptionHandling(handling -> handling.authenticationEntryPoint(basicAuthenticationEntryPoint()));
        return http.build();
    }

    private AuthenticationEntryPoint basicAuthenticationEntryPoint() {
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName(ACTUATOR_REALM);
        return entryPoint;
    }

}
