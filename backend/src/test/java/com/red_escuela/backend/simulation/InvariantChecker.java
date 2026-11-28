package com.red_escuela.backend.simulation;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InvariantChecker {

    private static final String FOREIGN_KEYS = """
            select c.conrelid::regclass::text as child_table, a.attname as child_column,
                   c.confrelid::regclass::text as parent_table, pa.attname as parent_column
            from pg_constraint c
            join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
            join pg_attribute pa on pa.attrelid = c.confrelid and pa.attnum = c.confkey[1]
            where c.contype = 'f' and array_length(c.conkey, 1) = 1
              and c.connamespace = 'public'::regnamespace
            order by 1, 2
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<String, InvariantViolation> violations = new LinkedHashMap<>();
    private int dailyChecks;

    public InvariantChecker(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void checkDay(LocalDate simulatedDay) {
        dailyChecks++;
        AcademicInvariants.daily().forEach(invariant -> record(run(invariant, simulatedDay)));
    }

    public void checkTerm(LocalDate simulatedDay) {
        record(findForeignKeyOrphans(simulatedDay));
    }

    public List<InvariantViolation> detect(String code, LocalDate simulatedDay) {
        if (AcademicInvariants.FOREIGN_KEY_ORPHAN.equals(code)) {
            return findForeignKeyOrphans(simulatedDay);
        }
        return run(AcademicInvariants.byCode(code), simulatedDay);
    }

    public List<InvariantViolation> violations() {
        return List.copyOf(violations.values());
    }

    public List<InvariantViolation> violationsOf(String code) {
        return violations.values().stream().filter(violation -> violation.rule().equals(code)).toList();
    }

    public int dailyChecks() {
        return dailyChecks;
    }

    private List<InvariantViolation> run(AcademicInvariant invariant, LocalDate simulatedDay) {
        return jdbc.query(invariant.sql(), new MapSqlParameterSource("today", Date.valueOf(simulatedDay)), (rs, row) -> {
            Date factDay = rs.getDate("fact_day");
            long studentId = rs.getLong("student_id");
            return new InvariantViolation(invariant.code(), simulatedDay, factDay == null ? null : factDay.toLocalDate(),
                    rs.wasNull() ? null : studentId, rs.getString("detail"));
        });
    }

    private List<InvariantViolation> findForeignKeyOrphans(LocalDate simulatedDay) {
        List<InvariantViolation> found = new ArrayList<>();
        for (Map<String, Object> key : jdbc.getJdbcTemplate().queryForList(FOREIGN_KEYS)) {
            String child = (String) key.get("child_table");
            String childColumn = (String) key.get("child_column");
            String parent = (String) key.get("parent_table");
            String parentColumn = (String) key.get("parent_column");
            Long orphans = jdbc.getJdbcTemplate().queryForObject("select count(*) from " + child + " c where c." + quote(childColumn)
                    + " is not null and not exists (select 1 from " + parent + " p where p." + quote(parentColumn) + " = c." + quote(childColumn) + ")", Long.class);
            if (orphans != null && orphans > 0) {
                found.add(new InvariantViolation(AcademicInvariants.FOREIGN_KEY_ORPHAN, simulatedDay, simulatedDay, null,
                        child + "." + childColumn + " -> " + parent + "." + parentColumn + " orphans " + orphans));
            }
        }
        return found;
    }

    private static String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    private void record(List<InvariantViolation> found) {
        found.forEach(violation -> violations.putIfAbsent(violation.key(), violation));
    }
}
