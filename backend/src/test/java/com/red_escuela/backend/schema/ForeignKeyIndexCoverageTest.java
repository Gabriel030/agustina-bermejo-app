package com.red_escuela.backend.schema;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Migrates a real database to the latest version to compare its foreign keys with its indexes")
@Tag("migration")
class ForeignKeyIndexCoverageTest {

    private static final String UNINDEXED_FOREIGN_KEYS = """
            select t.relname || '.' || array_to_string(array(
                       select a.attname from unnest(c.conkey) with ordinality k(attnum, ord)
                       join pg_attribute a on a.attrelid = t.oid and a.attnum = k.attnum order by k.ord), ',')
            from pg_constraint c
            join pg_class t on t.oid = c.conrelid
            join pg_namespace n on n.oid = t.relnamespace
            where c.contype = 'f' and n.nspname = current_schema()
              and not exists (
                  select 1 from pg_index i
                  where i.indrelid = c.conrelid and i.indisvalid
                    and (string_to_array(i.indkey::text, ' ')::int2[])[1:array_length(c.conkey, 1)] = c.conkey)
            order by 1
            """;

    private static final String AUDIT_ACTOR = "Audit actor reference (who did it): written once, never filtered by, person rows are never deleted";

    private static final String COMPOSITE_CONSISTENCY = "Composite consistency FK (parent id plus currency or account): guarantees integrity only, parent rows are never deleted";

    private static final String LOW_TRAFFIC = "Not filtered by in any repository query today: add its index together with the first query that filters on it, then remove it from this list";

    private static final Map<String, String> ACCEPTED_WITHOUT_INDEX = acceptedWithoutIndex();

    private static Map<String, String> acceptedWithoutIndex() {
        Map<String, String> accepted = new TreeMap<>();
        accepted.put("academic_offering_cycle.created_by", AUDIT_ACTOR);
        accepted.put("academic_offering_cycle.decided_by", AUDIT_ACTOR);
        accepted.put("academic_offering_cycle.submitted_by", AUDIT_ACTOR);
        accepted.put("announcement.created_by_id", AUDIT_ACTOR);
        accepted.put("annual_upfront_request.accepted_by_person_id", AUDIT_ACTOR);
        accepted.put("annual_upfront_request.requested_by_person_id", AUDIT_ACTOR);
        accepted.put("annual_upfront_terms_version.published_by_person_id", AUDIT_ACTOR);
        accepted.put("attendance.justified_by_id", AUDIT_ACTOR);
        accepted.put("attendance.recorded_by_id", AUDIT_ACTOR);
        accepted.put("attendance_change_request.approved_by_id", AUDIT_ACTOR);
        accepted.put("attendance_change_request.requested_by_id", AUDIT_ACTOR);
        accepted.put("attendance_correction.corrected_by_id", AUDIT_ACTOR);
        accepted.put("audit_log.actor_person_id", AUDIT_ACTOR);
        accepted.put("authorization_request.decided_by_id", AUDIT_ACTOR);
        accepted.put("authorization_request.requested_by_id", AUDIT_ACTOR);
        accepted.put("authorization_request_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("bank_transfer_receipt.reviewed_by_id", AUDIT_ACTOR);
        accepted.put("bank_transfer_receipt.uploaded_by_id", AUDIT_ACTOR);
        accepted.put("broadcast.cancelled_by_person_id", AUDIT_ACTOR);
        accepted.put("broadcast.confirmed_by_person_id", AUDIT_ACTOR);
        accepted.put("broadcast.created_by_person_id", AUDIT_ACTOR);
        accepted.put("certificate_exception_approval.decided_by_id", AUDIT_ACTOR);
        accepted.put("certificate_exception_approval.requested_by_id", AUDIT_ACTOR);
        accepted.put("certificate_exception_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("certificate_issuance.requested_by_id", AUDIT_ACTOR);
        accepted.put("chargeback.registered_by_person_id", AUDIT_ACTOR);
        accepted.put("chargeback.reversed_by_person_id", AUDIT_ACTOR);
        accepted.put("class_session.created_by_id", AUDIT_ACTOR);
        accepted.put("commission_closure.closed_by_id", AUDIT_ACTOR);
        accepted.put("commission_closure.confirmed_by_id", AUDIT_ACTOR);
        accepted.put("commission_closure_audit.actor_person_id", AUDIT_ACTOR);
        accepted.put("commission_closure_exception.granted_by_person_id", AUDIT_ACTOR);
        accepted.put("commission_closure_exception.revoked_by_person_id", AUDIT_ACTOR);
        accepted.put("configuration_version.cancelled_by_id", AUDIT_ACTOR);
        accepted.put("configuration_version.decided_by_id", AUDIT_ACTOR);
        accepted.put("configuration_version.proposed_by_id", AUDIT_ACTOR);
        accepted.put("configuration_version_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("credit_note.issued_by_person_id", AUDIT_ACTOR);
        accepted.put("director_approval_request.decided_by_id", AUDIT_ACTOR);
        accepted.put("director_approval_request.requested_by_id", AUDIT_ACTOR);
        accepted.put("discount_definition.closed_by_id", AUDIT_ACTOR);
        accepted.put("discount_definition.created_by_id", AUDIT_ACTOR);
        accepted.put("discount_definition_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("enrollment_fee_exemption.granted_by_id", AUDIT_ACTOR);
        accepted.put("enrollment_fee_exemption.revoked_by_id", AUDIT_ACTOR);
        accepted.put("enrollment_payment_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("feature_change_log.actor_person_id", AUDIT_ACTOR);
        accepted.put("grade_item.recovery_loaded_by", AUDIT_ACTOR);
        accepted.put("grade_item_audit.changed_by_id", AUDIT_ACTOR);
        accepted.put("graduation_certificate.issued_by_id", AUDIT_ACTOR);
        accepted.put("graduation_certificate.revoked_by_id", AUDIT_ACTOR);
        accepted.put("graduation_request.decided_by_id", AUDIT_ACTOR);
        accepted.put("graduation_request.verified_by_id", AUDIT_ACTOR);
        accepted.put("identity_contact_change_log.actor_person_id", AUDIT_ACTOR);
        accepted.put("import_batch.created_by_person_id", AUDIT_ACTOR);
        accepted.put("import_batch.sealed_by_person_id", AUDIT_ACTOR);
        accepted.put("import_batch_event.actor_person_id", AUDIT_ACTOR);
        accepted.put("institute_setting.updated_by_id", AUDIT_ACTOR);
        accepted.put("institute_setting_change_log.actor_person_id", AUDIT_ACTOR);
        accepted.put("invoice_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("message_template.created_by_person_id", AUDIT_ACTOR);
        accepted.put("payment.registered_by_person_id", AUDIT_ACTOR);
        accepted.put("payment.reviewed_by_person_id", AUDIT_ACTOR);
        accepted.put("payment_allocation.actor_person_id", AUDIT_ACTOR);
        accepted.put("payment_gateway_config.updated_by_id", AUDIT_ACTOR);
        accepted.put("payment_gateway_config_change.changed_by_id", AUDIT_ACTOR);
        accepted.put("payment_plan_option.created_by_id", AUDIT_ACTOR);
        accepted.put("payment_plan_selection.created_by_id", AUDIT_ACTOR);
        accepted.put("payment_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("platform_feature.updated_by_id", AUDIT_ACTOR);
        accepted.put("platform_provisioning_log.actor_person_id", AUDIT_ACTOR);
        accepted.put("price_version.cancelled_by_id", AUDIT_ACTOR);
        accepted.put("price_version.decided_by_id", AUDIT_ACTOR);
        accepted.put("price_version.proposed_by_id", AUDIT_ACTOR);
        accepted.put("price_version_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("program_cohort.created_by_id", AUDIT_ACTOR);
        accepted.put("program_enrollment_application.decided_by_id", AUDIT_ACTOR);
        accepted.put("program_year_billing_template.created_by_id", AUDIT_ACTOR);
        accepted.put("refund.requested_by_person_id", AUDIT_ACTOR);
        accepted.put("refund_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("regular_certificate_request.requested_by_id", AUDIT_ACTOR);
        accepted.put("regular_certificate_request.resolved_by_id", AUDIT_ACTOR);
        accepted.put("regular_student_certificate.issued_by_id", AUDIT_ACTOR);
        accepted.put("regular_student_certificate.revoked_by_id", AUDIT_ACTOR);
        accepted.put("regular_student_certificate.standing_confirmed_by_id", AUDIT_ACTOR);
        accepted.put("section_evaluation_config.reviewed_by_id", AUDIT_ACTOR);
        accepted.put("section_grade_item_publication.published_by_id", AUDIT_ACTOR);
        accepted.put("student.registered_by_person_id", AUDIT_ACTOR);
        accepted.put("student_discount.granted_by_id", AUDIT_ACTOR);
        accepted.put("student_discount.revoked_by_id", AUDIT_ACTOR);
        accepted.put("student_discount_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("student_status_change.actor_person_id", AUDIT_ACTOR);
        accepted.put("chargeback.payment_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("credit_note.invoice_id,currency", COMPOSITE_CONSISTENCY);
        accepted.put("credit_note.invoice_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("current_account_entry.compensates_entry_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("current_account_entry.credit_note_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("current_account_entry.invoice_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("current_account_entry.payment_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("current_account_entry.refund_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("current_account_entry.student_current_account_id,currency", COMPOSITE_CONSISTENCY);
        accepted.put("invoice.origin_invoice_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("invoice.student_current_account_id,currency", COMPOSITE_CONSISTENCY);
        accepted.put("invoice_discount.invoice_line_id,currency", COMPOSITE_CONSISTENCY);
        accepted.put("invoice_line.invoice_id,currency", COMPOSITE_CONSISTENCY);
        accepted.put("late_fee_charge.debit_note_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("late_fee_charge.origin_invoice_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("late_fee_charge_correction.credit_note_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("late_fee_charge_correction.late_fee_charge_id,debit_note_id", COMPOSITE_CONSISTENCY);
        accepted.put("late_fee_charge_correction.settlement_allocation_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("payment.student_current_account_id,currency", COMPOSITE_CONSISTENCY);
        accepted.put("payment_allocation.invoice_id,amount_currency", COMPOSITE_CONSISTENCY);
        accepted.put("payment_allocation.invoice_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("payment_allocation_source.credit_entry_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("payment_allocation_source.payment_allocation_id,amount_currency", COMPOSITE_CONSISTENCY);
        accepted.put("payment_allocation_source.payment_allocation_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("payment_checkout_item.payment_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("refund.payment_id,student_current_account_id", COMPOSITE_CONSISTENCY);
        accepted.put("refund.student_current_account_id,currency", COMPOSITE_CONSISTENCY);
        accepted.put("academic_offering_cycle.term_id", LOW_TRAFFIC);
        accepted.put("annual_upfront_request.lective_year_term_id", LOW_TRAFFIC);
        accepted.put("attendance_change_request.attendance_id", LOW_TRAFFIC);
        accepted.put("broadcast.template_id", LOW_TRAFFIC);
        accepted.put("broadcast_recipient.person_id", LOW_TRAFFIC);
        accepted.put("certificate_exception_approval.course_enrollment_id", LOW_TRAFFIC);
        accepted.put("certificate_exception_approval.program_enrollment_id", LOW_TRAFFIC);
        accepted.put("certificate_issuance.program_enrollment_id", LOW_TRAFFIC);
        accepted.put("certificate_template_version.first_signer_signature_image_id", LOW_TRAFFIC);
        accepted.put("certificate_template_version.logo_image_id", LOW_TRAFFIC);
        accepted.put("certificate_template_version.second_signer_signature_image_id", LOW_TRAFFIC);
        accepted.put("configuration_version.cohort_id", LOW_TRAFFIC);
        accepted.put("configuration_version.course_id", LOW_TRAFFIC);
        accepted.put("configuration_version.previous_version_id", LOW_TRAFFIC);
        accepted.put("configuration_version.program_id", LOW_TRAFFIC);
        accepted.put("configuration_version.term_id", LOW_TRAFFIC);
        accepted.put("course.term_id", LOW_TRAFFIC);
        accepted.put("course_enrollment_application.course_id", LOW_TRAFFIC);
        accepted.put("course_enrollment_application.payment_receipt_id", LOW_TRAFFIC);
        accepted.put("course_section.academic_offering_cycle_id", LOW_TRAFFIC);
        accepted.put("course_section.course_subject_id", LOW_TRAFFIC);
        accepted.put("course_section.evaluation_scheme_id", LOW_TRAFFIC);
        accepted.put("course_section_instructor.instructor_id", LOW_TRAFFIC);
        accepted.put("course_subject.course_id", LOW_TRAFFIC);
        accepted.put("course_subject_prerequisite.prerequisite_course_subject_id", LOW_TRAFFIC);
        accepted.put("credit_note.authorization_request_id", LOW_TRAFFIC);
        accepted.put("director_approval_request.program_enrollment_id", LOW_TRAFFIC);
        accepted.put("director_approval_request.subject_id", LOW_TRAFFIC);
        accepted.put("discount_definition.program_cohort_id", LOW_TRAFFIC);
        accepted.put("email_verification_token.person_id", LOW_TRAFFIC);
        accepted.put("enrollment_fee_exemption.template_id", LOW_TRAFFIC);
        accepted.put("exam_board.course_subject_id", LOW_TRAFFIC);
        accepted.put("exam_board.term_id", LOW_TRAFFIC);
        accepted.put("exam_board_instructor.instructor_id", LOW_TRAFFIC);
        accepted.put("late_fee_charge.late_payment_version_id", LOW_TRAFFIC);
        accepted.put("late_fee_charge.settlement_allocation_id", LOW_TRAFFIC);
        accepted.put("login_attempt.person_id", LOW_TRAFFIC);
        accepted.put("password_reset_token.person_id", LOW_TRAFFIC);
        accepted.put("payment_plan_option.course_id", LOW_TRAFFIC);
        accepted.put("payment_plan_option.program_id", LOW_TRAFFIC);
        accepted.put("payment_plan_selection.course_enrollment_id", LOW_TRAFFIC);
        accepted.put("payment_plan_selection.payment_plan_option_id", LOW_TRAFFIC);
        accepted.put("payment_plan_selection.program_enrollment_id", LOW_TRAFFIC);
        accepted.put("permission_discrepancy_counter.permission_id", LOW_TRAFFIC);
        accepted.put("platform_provisioning_log.director_person_id", LOW_TRAFFIC);
        accepted.put("platform_provisioning_log.institution_id", LOW_TRAFFIC);
        accepted.put("price_version.previous_version_id", LOW_TRAFFIC);
        accepted.put("program_elective_group.elective_group_id", LOW_TRAFFIC);
        accepted.put("program_enrollment.current_billing_template_id", LOW_TRAFFIC);
        accepted.put("program_enrollment.program_cohort_id", LOW_TRAFFIC);
        accepted.put("program_enrollment_application.program_cohort_id", LOW_TRAFFIC);
        accepted.put("program_enrollment_application.program_id", LOW_TRAFFIC);
        accepted.put("program_subject.elective_group_id", LOW_TRAFFIC);
        accepted.put("program_subject.subject_id", LOW_TRAFFIC);
        accepted.put("program_subject_corequisite.corequisite_program_subject_id", LOW_TRAFFIC);
        accepted.put("program_subject_prerequisite.prerequisite_program_subject_id", LOW_TRAFFIC);
        accepted.put("program_year_billing_template.term_id", LOW_TRAFFIC);
        accepted.put("regular_certificate_request.certificate_id", LOW_TRAFFIC);
        accepted.put("regular_certificate_request.course_enrollment_id", LOW_TRAFFIC);
        accepted.put("regular_certificate_request.program_enrollment_id", LOW_TRAFFIC);
        accepted.put("regular_student_certificate.request_id", LOW_TRAFFIC);
        accepted.put("role_permission.permission_id", LOW_TRAFFIC);
        accepted.put("section_modification_proposal_slot.proposal_id", LOW_TRAFFIC);
        accepted.put("student.institution_id", LOW_TRAFFIC);
        accepted.put("subject_section.academic_offering_cycle_id", LOW_TRAFFIC);
        accepted.put("subject_section.evaluation_scheme_id", LOW_TRAFFIC);
        accepted.put("subject_section_instructor.instructor_id", LOW_TRAFFIC);
        return accepted;
    }

    @Test
    void everyForeignKeyIsCoveredByAnIndexOrExplicitlyAccepted() {
        Set<String> unindexed = unindexedForeignKeys();
        Set<String> unexplained = new TreeSet<>(unindexed);
        unexplained.removeAll(ACCEPTED_WITHOUT_INDEX.keySet());
        assertThat(unexplained).as("foreign keys without a covering index and without an accepted reason").isEmpty();
    }

    @Test
    void acceptedListOnlyContainsForeignKeysThatStillLackAnIndex() {
        Set<String> unindexed = unindexedForeignKeys();
        Set<String> stale = new TreeSet<>(ACCEPTED_WITHOUT_INDEX.keySet());
        stale.removeAll(unindexed);
        assertThat(stale).as("accepted entries that are now indexed or no longer exist: remove them").isEmpty();
    }

    @Test
    void everyAcceptedForeignKeyStatesItsReason() {
        assertThat(ACCEPTED_WITHOUT_INDEX.values()).allMatch(reason -> reason != null && reason.length() >= 10);
    }

    private static Set<String> unindexedForeignKeys() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(latestMigrationVersion())) {
            return new TreeSet<>(new JdbcTemplate(dataSource).queryForList(UNINDEXED_FOREIGN_KEYS, String.class));
        }
    }

    private static String latestMigrationVersion() {
        try {
            return Arrays.stream(new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql"))
                    .map(resource -> resource.getFilename().substring(1, resource.getFilename().indexOf("__")))
                    .max(Comparator.comparingInt(Integer::parseInt))
                    .orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
