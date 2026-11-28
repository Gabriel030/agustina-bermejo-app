package com.red_escuela.backend.simulation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InvariantSabotage {

    private static final Map<String, List<String>> MUTATIONS = new LinkedHashMap<>();

    static {
        MUTATIONS.put(AcademicInvariants.ENROLLMENT_WITHOUT_COHORT, List.of(
                "update program_enrollment set program_cohort_id = null where id = (select min(id) from program_enrollment)"));
        MUTATIONS.put(AcademicInvariants.SECTION_OVER_CAPACITY, List.of("""
                update subject_section_enrollment set status = 'ENROLLED' where id in (
                    select id from subject_section_enrollment where status = 'APPROVED' and program_section_id = (
                        select program_section_id from subject_section_enrollment where status = 'APPROVED'
                        group by program_section_id having count(*) >= 2 order by program_section_id limit 1)
                    order by id limit 2)
                """, """
                update subject_section set capacity = 1 where id = (
                    select program_section_id from subject_section_enrollment where status = 'ENROLLED'
                    group by program_section_id having count(*) >= 2 order by program_section_id limit 1)
                """));
        MUTATIONS.put(AcademicInvariants.GRADE_ITEM_OUTSIDE_SCHEME, List.of(
                "update grade_item set sequence_number = 99 where id = (select min(id) from grade_item where type = 'EXAM' and program_section_enrollment_id is not null and exam_board_id is null)"));
        MUTATIONS.put(AcademicInvariants.APPROVED_BELOW_SCHEME_THRESHOLD, List.of(
                "update subject_section_enrollment set final_score = 1 where id = (select min(id) from subject_section_enrollment where status = 'APPROVED' and final_score is not null)"));
        MUTATIONS.put(AcademicInvariants.PREREQUISITE_NOT_MET, List.of("""
                with target as (
                    select sse.id as enrollment_id, sse.program_enrollment_id, pps.subject_id as prerequisite_subject_id
                    from subject_section_enrollment sse
                    join subject_section ss on ss.id = sse.program_section_id
                    join program_enrollment pe on pe.id = sse.program_enrollment_id
                    join program_subject ps on ps.program_id = pe.program_id and ps.plan_id = pe.plan_id and ps.subject_id = ss.subject_id
                    join program_subject_prerequisite pr on pr.program_subject_id = ps.id
                    join program_subject pps on pps.id = pr.prerequisite_program_subject_id
                    where sse.status = 'APPROVED' order by sse.id limit 1)
                update subject_section_enrollment prior set status = 'FAILED'
                from target
                where prior.program_enrollment_id = target.program_enrollment_id
                  and prior.program_section_id in (select id from subject_section where subject_id = target.prerequisite_subject_id)
                """));
        MUTATIONS.put(AcademicInvariants.ATTENDANCE_OUTSIDE_CLASS_DAY, List.of(
                "update attendance set class_date = date '2027-05-01' where id = (select min(id) from attendance where program_section_enrollment_id is not null)"));
        MUTATIONS.put(AcademicInvariants.CLOSED_SECTION_WITH_OPEN_ENROLLMENT, List.of("""
                update subject_section_enrollment set status = 'IN_PROGRESS' where id = (
                    select min(sse.id) from subject_section_enrollment sse
                    join commission_closure cc on cc.section_id = sse.program_section_id and cc.section_kind = 'PROGRAM' and cc.status = 'CLOSED')
                """));
        MUTATIONS.put(AcademicInvariants.WRITE_AFTER_CLOSURE, List.of("""
                update attendance set class_date = (select cc.closed_at::date + 7 from commission_closure cc
                    join subject_section_enrollment sse on sse.program_section_id = cc.section_id
                    where cc.section_kind = 'PROGRAM' and cc.status = 'CLOSED' and sse.id = attendance.program_section_enrollment_id)
                where id = (select min(a.id) from attendance a
                    join subject_section_enrollment sse on sse.id = a.program_section_enrollment_id
                    join commission_closure cc on cc.section_id = sse.program_section_id and cc.section_kind = 'PROGRAM' and cc.status = 'CLOSED')
                """));
        MUTATIONS.put(AcademicInvariants.EXAM_BOARD_WINDOW_INVALID, List.of(
                "update exam_board set registration_end = registration_start - interval '1 day' where id = (select min(id) from exam_board where status = 'CLOSED')"));
        MUTATIONS.put(AcademicInvariants.EXAM_BOARD_UNRESOLVED_REGISTRATION, List.of(
                "update exam_board_registration set status = 'REGISTERED' where id = (select min(ebr.id) from exam_board_registration ebr join exam_board eb on eb.id = ebr.exam_board_id where eb.status = 'CLOSED' and ebr.program_section_enrollment_id is not null)"));
        MUTATIONS.put(AcademicInvariants.EXAM_REGISTRATION_WITHOUT_GRADE, List.of(
                "delete from grade_item where id = (select min(id) from grade_item where type = 'FINAL' and exam_board_id is not null and program_section_enrollment_id is not null)"));
        MUTATIONS.put(AcademicInvariants.EXAM_ATTEMPTS_EXCEEDED, List.of(
                "update subject set max_final_exam_attempts = 0 where id = (select eb.subject_id from exam_board_registration ebr join exam_board eb on eb.id = ebr.exam_board_id where ebr.status = 'GRADED' and eb.subject_id is not null order by ebr.id limit 1)"));
        MUTATIONS.put(AcademicInvariants.EXAM_REGISTRATION_SUBJECT_MISMATCH, List.of(
                "update exam_board set subject_id = (select max(id) from subject) where id = (select min(eb.id) from exam_board eb join exam_board_registration ebr on ebr.exam_board_id = eb.id where eb.subject_id <> (select max(id) from subject) and ebr.program_section_enrollment_id is not null)"));
        MUTATIONS.put(AcademicInvariants.COMPLETED_WITHOUT_APPROVED_GRADUATION, List.of(
                "update graduation_request set status = 'REJECTED' where program_enrollment_id = (select min(id) from program_enrollment where status = 'COMPLETED')"));
        MUTATIONS.put(AcademicInvariants.GRADUATION_WITH_PENDING_SUBJECTS, List.of("""
                update subject_section_enrollment set status = 'FAILED' where id = (
                    select min(sse.id) from subject_section_enrollment sse
                    join graduation_request gr on gr.program_enrollment_id = sse.program_enrollment_id
                    where sse.status = 'APPROVED' and gr.status <> 'REJECTED')
                """));
        MUTATIONS.put(AcademicInvariants.ENROLLMENT_STUDENT_MISMATCH, List.of(
                "update subject_section_enrollment set student_id = student_id + 100000 where id = (select min(id) from subject_section_enrollment)"));
        MUTATIONS.put(AcademicInvariants.ENROLLMENT_PROGRAM_MISMATCH, List.of(
                "update program_enrollment set program_id = program_id + 100000 where id = (select min(program_enrollment_id) from subject_section_enrollment)"));
        MUTATIONS.put(AcademicInvariants.SECTION_WITHOUT_APPROVED_CYCLE, List.of(
                "update subject_section set academic_offering_cycle_id = null where id = (select min(id) from subject_section where status = 'ACTIVE')"));
        MUTATIONS.put(AcademicInvariants.APPROVAL_PENDING_WITH_REQUIREMENTS_MET, List.of(
                "update subject_section_enrollment set status = 'APPROVAL_PENDING' where id = (select min(id) from subject_section_enrollment where status = 'APPROVED')"));
        MUTATIONS.put(AcademicInvariants.FOREIGN_KEY_ORPHAN, List.of(
                "update attendance set program_section_enrollment_id = 987654321 where id = (select min(id) from attendance where program_section_enrollment_id is not null)"));
    }

    private InvariantSabotage() {
    }

    public static Map<String, List<String>> mutations() {
        return MUTATIONS;
    }
}
