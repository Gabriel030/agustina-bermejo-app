package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class CertificateRegistryNumberAssigner {

    private static final String NEXT_NUMBER = """
            insert into graduation_certificate_registry_counter (kind, registry_year, last_number)
            values (?, ?, 1)
            on conflict (kind, registry_year) do update set last_number = graduation_certificate_registry_counter.last_number + 1
            returning last_number
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public CertificateRegistryNumberAssigner(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(GraduationCertificateKind kind) {
        int year = LocalDate.now(clock).getYear();
        Integer number = jdbcTemplate.queryForObject(NEXT_NUMBER, Integer.class, kind.name(), year);
        return String.format("%07d/%d", number, year);
    }
}
