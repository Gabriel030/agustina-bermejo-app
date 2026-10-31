package com.red_escuela.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${app.cors.allowed-origins}") List<String> corsAllowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    private static AuthorizationDecision authenticatedExceptPlatformAdmin(
            Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        boolean authenticated = current != null && current.isAuthenticated()
                && !(current instanceof AnonymousAuthenticationToken);
        boolean platformAdmin = authenticated && current.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + PLATFORM_ADMIN_ROLE).equals(authority.getAuthority()));
        return new AuthorizationDecision(authenticated && !platformAdmin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Total-Count"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/prospects").permitAll()
                .requestMatchers(HttpMethod.GET, "/institutions").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/payments/mp-webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/verify-email").permitAll()
                .requestMatchers(HttpMethod.GET, "/public/modules").permitAll()
                .requestMatchers("/platform/**").hasRole(PLATFORM_ADMIN_ROLE)
                .requestMatchers("/me/modules").authenticated()
                .anyRequest().access(SecurityConfig::authenticatedExceptPlatformAdmin))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
