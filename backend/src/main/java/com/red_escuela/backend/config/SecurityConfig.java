package com.red_escuela.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${app.cors.allowed-origins}") List<String> corsAllowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Hoy no hay frontend desplegado — default a app.cors.allowed-origins (puerto de Vite),
    // overrideable por CORS_ALLOWED_ORIGINS el día que haya un dominio real de despliegue.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // Spec Gestión de personas: primer cambio de comportamiento real de la security chain
    // desde que se armó en la spec de auth — hasta acá todo pasaba. /auth/** y el alta
    // pública de prospectos (POST /prospects, primer paso del proceso de matriculación,
    // sección 5 de la spec funcional) quedan permitAll(); todo lo demás exige JWT válido.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // X-Content-Type-Options: nosniff y X-Frame-Options: DENY ya son default de
            // Spring Security 6 sin necesidad de configurarlos acá — el único header que
            // sí falta por default es HSTS.
            .headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/prospects").permitAll()
                // Épica 7 Fase 2: Registrarse.jsx todavía no tiene sesión cuando arma el
                // form, necesita resolver el institutionId de RegisterRequest antes.
                .requestMatchers(HttpMethod.GET, "/institutions").permitAll()
                // Sin esto, CUALQUIER excepción que Spring resuelve internamente vía
                // response.sendError(...) — no solo una nuestra sin capturar, también las
                // propias de Spring MVC (HttpMessageNotReadableException por JSON
                // malformado, MethodArgumentTypeMismatchException por un path variable con
                // tipo incorrecto, etc.) — dispara un redirect interno a /error, que sin
                // este permitAll cae en anyRequest().authenticated() y devuelve un 403
                // vacío y engañoso en vez del código real (400, 500, lo que corresponda).
                // Confirmado con curl: un POST /staff con JSON malformado daba 403 en vez
                // de 400. NO reintroducir acá un catch-all @ExceptionHandler(Exception.class)
                // en GlobalExceptionHandler como "solución alternativa" a esto — se probó y
                // rompe @PreAuthorize (AccessDeniedException se resuelve en el mismo
                // dispatch de Spring MVC que un @RestControllerAdvice, ANTES de llegar a la
                // capa de filtros — un catch-all ahí la intercepta y convierte 403 reales en
                // 500). /error en cambio es un dispatch de servlet completamente aparte, sin
                // pasar por ningún HandlerMethod ni @PreAuthorize — permitAll acá es seguro.
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/payments/mp-webhook").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
