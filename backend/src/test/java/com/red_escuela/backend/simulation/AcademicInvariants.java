package com.red_escuela.backend.simulation;

import java.util.List;

public final class AcademicInvariants {

    public static final String ENROLLMENT_WITHOUT_COHORT = "ENROLLMENT_WITHOUT_COHORT";
    public static final String SECTION_OVER_CAPACITY = "SECTION_OVER_CAPACITY";
    public static final String GRADE_ITEM_OUTSIDE_SCHEME = "GRADE_ITEM_OUTSIDE_SCHEME";
    public static final String APPROVED_BELOW_SCHEME_THRESHOLD = "APPROVED_BELOW_SCHEME_THRESHOLD";
    public static final String PREREQUISITE_NOT_MET = "PREREQUISITE_NOT_MET";
    public static final String ATTENDANCE_OUTSIDE_CLASS_DAY = "ATTENDANCE_OUTSIDE_CLASS_DAY";
    public static final String CLOSED_SECTION_WITH_OPEN_ENROLLMENT = "CLOSED_SECTION_WITH_OPEN_ENROLLMENT";
    public static final String WRITE_AFTER_CLOSURE = "WRITE_AFTER_CLOSURE";
    public static final String EXAM_BOARD_WINDOW_INVALID = "EXAM_BOARD_WINDOW_INVALID";
    public static final String EXAM_BOARD_UNRESOLVED_REGISTRATION = "EXAM_BOARD_UNRESOLVED_REGISTRATION";
    public static final String EXAM_REGISTRATION_WITHOUT_GRADE = "EXAM_REGISTRATION_WITHOUT_GRADE";
    public static final String EXAM_ATTEMPTS_EXCEEDED = "EXAM_ATTEMPTS_EXCEEDED";
    public static final String EXAM_REGISTRATION_SUBJECT_MISMATCH = "EXAM_REGISTRATION_SUBJECT_MISMATCH";
    public static final String COMPLETED_WITHOUT_APPROVED_GRADUATION = "COMPLETED_WITHOUT_APPROVED_GRADUATION";
    public static final String GRADUATION_WITH_PENDING_SUBJECTS = "GRADUATION_WITH_PENDING_SUBJECTS";
    public static final String ENROLLMENT_STUDENT_MISMATCH = "ENROLLMENT_STUDENT_MISMATCH";
    public static final String ENROLLMENT_PROGRAM_MISMATCH = "ENROLLMENT_PROGRAM_MISMATCH";
    public static final String SECTION_WITHOUT_APPROVED_CYCLE = "SECTION_WITHOUT_APPROVED_CYCLE";
    public static final String FOREIGN_KEY_ORPHAN = "FOREIGN_KEY_ORPHAN";

    private static final String OCCUPYING = "('ENROLLED','IN_PROGRESS','REGULAR')";
    private static final String ACTIVE_ENROLLMENT = "('ENROLLED','IN_PROGRESS','REGULAR','APPROVED','FAILED')";

    private static final List<AcademicInvariant> DAILY = List.of(
            new AcademicInvariant(ENROLLMENT_WITHOUT_COHORT, """
                    select pe.enrollment_date::date as fact_day, pe.student_id as student_id,
                           'program_enrollment ' || pe.id || ' status ' || pe.status as detail
                    from program_enrollment pe
                    where pe.program_cohort_id is null
                    """),
            new AcademicInvariant(SECTION_OVER_CAPACITY, """
                    select cast(:today as date) as fact_day, cast(null as bigint) as student_id,
                           'section ' || ss.id || ' capacity ' || ss.capacity || ' occupied ' || count(*) as detail
                    from subject_section ss
                    join subject_section_enrollment sse on sse.program_section_id = ss.id
                    where ss.capacity is not null and sse.status in %s
                    group by ss.id, ss.capacity
                    having count(*) > ss.capacity
                    """.formatted(OCCUPYING)),
            new AcademicInvariant(GRADE_ITEM_OUTSIDE_SCHEME, """
                    select gi.date::date as fact_day, sse.student_id as student_id,
                           'grade_item ' || gi.id || ' ' || gi.type || ' #' || gi.sequence_number || ' scheme ' ||
                           coalesce(es.name, 'none') || ' exams ' || coalesce(es.exam_count_expected, 0) as detail
                    from grade_item gi
                    join subject_section_enrollment sse on sse.id = gi.program_section_enrollment_id
                    join subject_section ss on ss.id = sse.program_section_id
                    left join evaluation_scheme es on es.id = ss.evaluation_scheme_id
                    where gi.exam_board_id is null
                      and (es.id is null
                           or (gi.type = 'EXAM' and gi.sequence_number > es.exam_count_expected)
                           or (gi.type = 'TP' and gi.sequence_number > es.tp_count_expected))
                    """),
            new AcademicInvariant(APPROVED_BELOW_SCHEME_THRESHOLD, """
                    select coalesce(sse.final_score_date::date, cast(:today as date)) as fact_day, sse.student_id as student_id,
                           'enrollment ' || sse.id || ' ' || sse.final_grade_type || ' score ' || sse.final_score || ' threshold ' ||
                           case when sse.final_grade_type = 'PROMOTION' then es.promotion_min_grade else es.min_approval_grade end as detail
                    from subject_section_enrollment sse
                    join subject_section ss on ss.id = sse.program_section_id
                    join evaluation_scheme es on es.id = ss.evaluation_scheme_id
                    where sse.status = 'APPROVED' and sse.final_score is not null
                      and sse.final_score < case when sse.final_grade_type = 'PROMOTION' then es.promotion_min_grade else es.min_approval_grade end
                    """),
            new AcademicInvariant(PREREQUISITE_NOT_MET, """
                    select sse.enrolled_at::date as fact_day, sse.student_id as student_id,
                           'enrollment ' || sse.id || ' ' || sub.name || ' lacks ' || pr.requirement_type || ' ' || psub.name as detail
                    from subject_section_enrollment sse
                    join subject_section ss on ss.id = sse.program_section_id
                    join subject sub on sub.id = ss.subject_id
                    join program_enrollment pe on pe.id = sse.program_enrollment_id
                    join program_subject ps on ps.program_id = pe.program_id and ps.plan_id = pe.plan_id and ps.subject_id = ss.subject_id
                    join program_subject_prerequisite pr on pr.program_subject_id = ps.id
                    join program_subject pps on pps.id = pr.prerequisite_program_subject_id
                    join subject psub on psub.id = pps.subject_id
                    where sse.status in %s
                      and not exists (
                        select 1 from subject_section_enrollment prior
                        join subject_section pss on pss.id = prior.program_section_id
                        where prior.program_enrollment_id = sse.program_enrollment_id
                          and pss.subject_id = pps.subject_id
                          and prior.id <> sse.id
                          and (prior.status = 'APPROVED' or (pr.requirement_type = 'PASSED_COURSEWORK' and prior.status = 'REGULAR')))
                    """.formatted(ACTIVE_ENROLLMENT)),
            new AcademicInvariant(ATTENDANCE_OUTSIDE_CLASS_DAY, """
                    select a.class_date as fact_day, sse.student_id as student_id,
                           'attendance ' || a.id || ' on ' || a.class_date || ' ' ||
                           case when a.class_date < t.start_date::date or a.class_date > t.end_date::date then 'outside term'
                                when extract(isodow from a.class_date) in (6, 7) then 'weekend'
                                when exists (select 1 from holiday h where h.date = a.class_date) then 'holiday'
                                when a.class_date > cast(:today as date) then 'future'
                                else 'no schedule slot that weekday' end as detail
                    from attendance a
                    join subject_section_enrollment sse on sse.id = a.program_section_enrollment_id
                    join subject_section ss on ss.id = sse.program_section_id
                    join term t on t.id = ss.term_id
                    where a.class_date < t.start_date::date or a.class_date > t.end_date::date
                       or extract(isodow from a.class_date) in (6, 7)
                       or exists (select 1 from holiday h where h.date = a.class_date)
                       or a.class_date > cast(:today as date)
                       or (exists (select 1 from section_schedule_slot sl where sl.subject_section_id = ss.id and sl.active)
                           and not exists (select 1 from section_schedule_slot sl where sl.subject_section_id = ss.id and sl.active
                                           and sl.day_of_week = upper(to_char(a.class_date, 'FMDay'))))
                    """),
            new AcademicInvariant(CLOSED_SECTION_WITH_OPEN_ENROLLMENT, """
                    select cc.closed_at::date as fact_day, sse.student_id as student_id,
                           'closure ' || cc.id || ' section ' || cc.section_id || ' enrollment ' || sse.id || ' still ' || sse.status as detail
                    from commission_closure cc
                    join subject_section_enrollment sse on sse.program_section_id = cc.section_id
                    where cc.section_kind = 'PROGRAM' and cc.status = 'CLOSED' and sse.status in ('ENROLLED', 'IN_PROGRESS')
                    """),
            new AcademicInvariant(WRITE_AFTER_CLOSURE, """
                    select a.class_date as fact_day, sse.student_id as student_id,
                           'closure ' || cc.id || ' closed ' || cc.closed_at::date || ' attendance ' || a.id || ' on ' || a.class_date as detail
                    from commission_closure cc
                    join subject_section_enrollment sse on sse.program_section_id = cc.section_id
                    join attendance a on a.program_section_enrollment_id = sse.id
                    where cc.section_kind = 'PROGRAM' and cc.status = 'CLOSED' and a.class_date > cc.closed_at::date
                    union all
                    select gi.date::date as fact_day, sse.student_id as student_id,
                           'closure ' || cc.id || ' closed ' || cc.closed_at::date || ' grade_item ' || gi.id || ' on ' || gi.date::date as detail
                    from commission_closure cc
                    join subject_section_enrollment sse on sse.program_section_id = cc.section_id
                    join grade_item gi on gi.program_section_enrollment_id = sse.id
                    where cc.section_kind = 'PROGRAM' and cc.status = 'CLOSED' and gi.exam_board_id is null and gi.date::date > cc.closed_at::date
                    union all
                    select sse.enrolled_at::date as fact_day, sse.student_id as student_id,
                           'closure ' || cc.id || ' closed ' || cc.closed_at::date || ' enrollment ' || sse.id || ' on ' || sse.enrolled_at::date as detail
                    from commission_closure cc
                    join subject_section_enrollment sse on sse.program_section_id = cc.section_id
                    where cc.section_kind = 'PROGRAM' and cc.status = 'CLOSED' and sse.enrolled_at::date > cc.closed_at::date
                    """),
            new AcademicInvariant(EXAM_BOARD_WINDOW_INVALID, """
                    select eb.exam_date::date as fact_day, cast(null as bigint) as student_id,
                           'exam_board ' || eb.id || ' registration ' || coalesce(eb.registration_start::text, 'null') || ' to ' ||
                           coalesce(eb.registration_end::text, 'null') || ' exam ' || eb.exam_date as detail
                    from exam_board eb
                    where eb.status <> 'CANCELED'
                      and (eb.registration_end is null or eb.registration_start is null
                           or eb.registration_start > eb.registration_end or eb.registration_end > eb.exam_date)
                    """),
            new AcademicInvariant(EXAM_BOARD_UNRESOLVED_REGISTRATION, """
                    select eb.exam_date::date as fact_day, sse.student_id as student_id,
                           'exam_board ' || eb.id || ' closed with registration ' || ebr.id || ' still REGISTERED' as detail
                    from exam_board eb
                    join exam_board_registration ebr on ebr.exam_board_id = eb.id
                    join subject_section_enrollment sse on sse.id = ebr.program_section_enrollment_id
                    where eb.status = 'CLOSED' and ebr.status = 'REGISTERED'
                    """),
            new AcademicInvariant(EXAM_REGISTRATION_WITHOUT_GRADE, """
                    select eb.exam_date::date as fact_day, sse.student_id as student_id,
                           'exam_board ' || eb.id || ' registration ' || ebr.id || ' GRADED without FINAL grade_item' as detail
                    from exam_board_registration ebr
                    join exam_board eb on eb.id = ebr.exam_board_id
                    join subject_section_enrollment sse on sse.id = ebr.program_section_enrollment_id
                    where ebr.status = 'GRADED'
                      and not exists (select 1 from grade_item gi where gi.exam_board_id = eb.id
                                      and gi.program_section_enrollment_id = sse.id and gi.type = 'FINAL' and gi.score is not null)
                    """),
            new AcademicInvariant(EXAM_ATTEMPTS_EXCEEDED, """
                    select cast(:today as date) as fact_day, sse.student_id as student_id,
                           'subject ' || sub.name || ' graded attempts ' || count(*) || ' limit ' || sub.max_final_exam_attempts as detail
                    from exam_board_registration ebr
                    join exam_board eb on eb.id = ebr.exam_board_id
                    join subject sub on sub.id = eb.subject_id
                    join subject_section_enrollment sse on sse.id = ebr.program_section_enrollment_id
                    where ebr.status = 'GRADED' and sub.max_final_exam_attempts is not null
                    group by sse.student_id, sub.id, sub.name, sub.max_final_exam_attempts
                    having count(*) > sub.max_final_exam_attempts
                    """),
            new AcademicInvariant(EXAM_REGISTRATION_SUBJECT_MISMATCH, """
                    select eb.exam_date::date as fact_day, sse.student_id as student_id,
                           'exam_board ' || eb.id || ' subject ' || eb.subject_id || ' registration ' || ebr.id || ' enrollment subject ' || ss.subject_id as detail
                    from exam_board_registration ebr
                    join exam_board eb on eb.id = ebr.exam_board_id
                    join subject_section_enrollment sse on sse.id = ebr.program_section_enrollment_id
                    join subject_section ss on ss.id = sse.program_section_id
                    where eb.subject_id is distinct from ss.subject_id
                    """),
            new AcademicInvariant(COMPLETED_WITHOUT_APPROVED_GRADUATION, """
                    select pe.completion_date::date as fact_day, pe.student_id as student_id,
                           'program_enrollment ' || pe.id || ' COMPLETED without APPROVED graduation request' as detail
                    from program_enrollment pe
                    where pe.status = 'COMPLETED'
                      and not exists (select 1 from graduation_request gr where gr.program_enrollment_id = pe.id and gr.status = 'APPROVED')
                    """),
            new AcademicInvariant(GRADUATION_WITH_PENDING_SUBJECTS, """
                    select gr.requested_at::date as fact_day, pe.student_id as student_id,
                           'graduation_request ' || gr.id || ' ' || gr.status || ' missing ' || sub.name as detail
                    from graduation_request gr
                    join program_enrollment pe on pe.id = gr.program_enrollment_id
                    join program_subject ps on ps.program_id = pe.program_id and ps.plan_id = pe.plan_id
                    join subject sub on sub.id = ps.subject_id
                    where gr.status <> 'REJECTED' and ps.category in ('MANDATORY', 'ADDITIONAL_MANDATORY') and ps.status = 'ACTIVE'
                      and not exists (select 1 from subject_section_enrollment sse
                                      join subject_section ss on ss.id = sse.program_section_id
                                      where sse.program_enrollment_id = pe.id and ss.subject_id = ps.subject_id and sse.status = 'APPROVED')
                    """),
            new AcademicInvariant(ENROLLMENT_STUDENT_MISMATCH, """
                    select sse.enrolled_at::date as fact_day, sse.student_id as student_id,
                           'enrollment ' || sse.id || ' student ' || sse.student_id || ' program_enrollment student ' || pe.student_id as detail
                    from subject_section_enrollment sse
                    join program_enrollment pe on pe.id = sse.program_enrollment_id
                    where sse.student_id <> pe.student_id
                    """),
            new AcademicInvariant(ENROLLMENT_PROGRAM_MISMATCH, """
                    select sse.enrolled_at::date as fact_day, sse.student_id as student_id,
                           'enrollment ' || sse.id || ' cycle program ' || c.program_id || ' program_enrollment program ' || pe.program_id as detail
                    from subject_section_enrollment sse
                    join program_enrollment pe on pe.id = sse.program_enrollment_id
                    join subject_section ss on ss.id = sse.program_section_id
                    join academic_offering_cycle c on c.id = ss.academic_offering_cycle_id
                    where c.program_id is distinct from pe.program_id
                    """),
            new AcademicInvariant(SECTION_WITHOUT_APPROVED_CYCLE, """
                    select ss.created_at::date as fact_day, cast(null as bigint) as student_id,
                           'section ' || ss.id || ' ' || ss.status || ' cycle ' || coalesce(c.status, 'none') as detail
                    from subject_section ss
                    left join academic_offering_cycle c on c.id = ss.academic_offering_cycle_id
                    where ss.status = 'ACTIVE' and (c.id is null or c.status in ('DRAFT', 'PENDING_APPROVAL', 'REJECTED'))
                    """));

    private AcademicInvariants() {
    }

    public static List<AcademicInvariant> daily() {
        return DAILY;
    }

    public static AcademicInvariant byCode(String code) {
        return DAILY.stream().filter(invariant -> invariant.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown invariant " + code));
    }

    public static List<String> codes() {
        return java.util.stream.Stream.concat(DAILY.stream().map(AcademicInvariant::code), java.util.stream.Stream.of(FOREIGN_KEY_ORPHAN)).toList();
    }
}
