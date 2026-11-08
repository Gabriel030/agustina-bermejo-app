package com.red_escuela.backend.testsupport;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class PerformanceIndexesMigrationTest {

    @Test
    void migrationCreatesEveryQueryBackedIndexOnItsExpectedColumns() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("201")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<String> definitions = jdbc.queryForList(
                    "select indexdef from pg_indexes where schemaname = current_schema() and indexname in ("
                            + "'ix_invoice_overdue', 'ix_invoice_line_period', 'ix_attendance_justification_pending', 'ix_attendance_justification_rejected', "
                            + "'ix_ebr_program_section_enrollment', 'ix_ebr_course_section_enrollment', 'ix_schedule_slot_subject_section', "
                            + "'ix_schedule_slot_course_section', 'ix_course_section_term', 'ix_course_section_enrollment_course_enrollment', "
                            + "'ix_refresh_token_person', 'ix_notification_recipient_created_at')", String.class);

            assertThat(definitions).hasSize(12);
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.invoice USING btree (id)") && d.contains("OVERDUE"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.invoice_line USING btree (period)"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.attendance USING btree (id)") && d.contains("JUSTIFICATION_PENDING"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.attendance USING btree (id)") && d.contains("justification_rejection_reason IS NOT NULL"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.exam_board_registration USING btree (program_section_enrollment_id)"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.exam_board_registration USING btree (course_section_enrollment_id)"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.section_schedule_slot USING btree (subject_section_id)"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.section_schedule_slot USING btree (course_section_id)"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.course_section USING btree (term_id)"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.course_section_enrollment USING btree (course_enrollment_id)"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.refresh_token USING btree (person_id)"));
            assertThat(definitions).anyMatch(d -> d.contains(" ON public.notification USING btree (recipient_id, created_at DESC)"));
        }
    }
}
