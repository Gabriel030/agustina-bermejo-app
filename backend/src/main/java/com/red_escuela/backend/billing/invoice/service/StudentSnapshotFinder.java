package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class StudentSnapshotFinder {

    private final JdbcTemplate jdbcTemplate;

    public StudentSnapshotFinder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StudentSnapshot findSnapshotOfStudent(Long studentId) {
        return jdbcTemplate.queryForObject("""
                select p.name, p.last_name, p.dni, a.street, a.number, a.city, a.state
                from student s join person p on p.id = s.person_id left join address a on a.id = p.address_id
                where s.id = ?
                """, (row, index) -> new StudentSnapshot(joinNonBlank(row.getString("name"), row.getString("last_name")), row.getString("dni"),
                nullIfBlank(joinNonBlank(row.getString("street"), row.getString("number"), row.getString("city"), row.getString("state")))), studentId);
    }

    private static String joinNonBlank(String... parts) {
        return Stream.of(parts).filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
    }

    private static String nullIfBlank(String value) {
        return value.isBlank() ? null : value;
    }
}
