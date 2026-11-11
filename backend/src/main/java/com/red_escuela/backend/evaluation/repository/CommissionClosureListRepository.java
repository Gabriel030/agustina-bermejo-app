package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.stereotype.Repository
public interface CommissionClosureListRepository extends Repository<CommissionClosure, Long> {

    String FILTERED_SECTIONS = """
            with sections as (
                select 'PROGRAM' as section_kind, s.id as section_id, sub.name as subject_name, s.name as section_name,
                       t.id as term_id, t.description as term_label, t.end_date as term_end
                from subject_section s
                join subject sub on sub.id = s.subject_id
                left join academic_offering_cycle cy on cy.id = s.academic_offering_cycle_id
                join term t on t.id = coalesce(cy.term_id, s.term_id)
                union all
                select 'COURSE', s.id, cs.name, s.name, t.id, t.description, t.end_date
                from course_section s
                join course_subject cs on cs.id = s.course_subject_id
                left join academic_offering_cycle cy on cy.id = s.academic_offering_cycle_id
                join term t on t.id = coalesce(cy.term_id, s.term_id)
            ),
            reopenings as (
                select closure_id, max(occurred_at) as reopened_at
                from commission_closure_audit
                where action = 'REOPENED'
                group by closure_id
            ),
            filtered as (
                select sec.section_kind, sec.section_id, sec.subject_name, sec.section_name, sec.term_id, sec.term_label,
                       c.id as closure_id, coalesce(c.status, 'DRAFT_AVAILABLE') as status, c.report_number, c.report_version,
                       c.book, c.folio,
                       case coalesce(c.status, 'DRAFT_AVAILABLE')
                           when 'TEACHER_CONFIRMED' then c.confirmed_at
                           when 'REOPENED' then r.reopened_at
                           when 'CLOSED' then c.closed_at
                       end as status_date
                from sections sec
                left join commission_closure c on c.section_kind = sec.section_kind and c.section_id = sec.section_id
                left join reopenings r on r.closure_id = c.id
                where sec.term_end < :now
                  and (cast(:termId as bigint) is null or sec.term_id = :termId)
                  and (cast(:pattern as text) is null
                       or unaccent(lower(sec.subject_name)) like unaccent(:pattern) escape '\\'
                       or unaccent(lower(sec.section_name)) like unaccent(:pattern) escape '\\')
            )
            """;

    @Query(value = FILTERED_SECTIONS + """
            select status as status, count(*) as total
            from filtered
            group by status
            """, nativeQuery = true)
    List<CommissionClosureStatusCount> countByStatus(@Param("now") LocalDateTime now, @Param("termId") Long termId,
            @Param("pattern") String pattern);

    @Query(value = FILTERED_SECTIONS + """
            select p.section_kind as sectionKind, p.section_id as sectionId, p.closure_id as closureId,
                   p.subject_name as subjectName, p.section_name as sectionName, p.term_id as termId,
                   p.term_label as termLabel, p.status as status, p.status_date as statusDate,
                   p.report_number as reportNumber, p.report_version as reportVersion, p.book as book, p.folio as folio,
                   case p.section_kind
                       when 'PROGRAM' then (
                           select string_agg(per.name || ' ' || per.last_name, chr(31) order by (ssi.role <> 'LEAD'), per.last_name, per.name)
                           from subject_section_instructor ssi
                           join instructor i on i.id = ssi.instructor_id
                           join person per on per.id = i.person_id
                           where ssi.program_section_id = p.section_id and (ssi.end_at is null or ssi.end_at > :now))
                       else (
                           select string_agg(per.name || ' ' || per.last_name, chr(31) order by (csi.role <> 'LEAD'), per.last_name, per.name)
                           from course_section_instructor csi
                           join instructor i on i.id = csi.instructor_id
                           join person per on per.id = i.person_id
                           where csi.course_section_id = p.section_id and (csi.end_at is null or csi.end_at > :now))
                   end as instructorNames,
                   case p.section_kind
                       when 'PROGRAM' then (
                           select count(*) from subject_section_enrollment e
                           where e.program_section_id = p.section_id and e.status in ('ENROLLED', 'IN_PROGRESS', 'REGULAR', 'APPROVED', 'FAILED'))
                       else (
                           select count(*) from course_section_enrollment e
                           where e.course_section_id = p.section_id and e.status in ('ENROLLED', 'IN_PROGRESS', 'REGULAR', 'APPROVED', 'FAILED'))
                   end as studentCount,
                   (select count(*) from commission_closure_student s where s.closure_id = p.closure_id) as snapshotCount,
                   (select count(*) from commission_closure_student s
                    where s.closure_id = p.closure_id and s.final_condition = 'PROMOTES') as promoteCount,
                   (select count(*) from commission_closure_student s
                    where s.closure_id = p.closure_id
                      and (s.final_condition = 'PROMOTION_CONDITIONAL' or s.correlatives_ok = false)) as toReviewCount
            from (
                select f.*
                from filtered f
                where (cast(:status as text) is null or f.status = :status)
                order by case f.status when 'TEACHER_CONFIRMED' then 0 when 'REOPENED' then 1 when 'DRAFT_AVAILABLE' then 2 else 3 end,
                         f.status_date desc nulls last, lower(f.subject_name), lower(f.section_name), f.section_kind, f.section_id
                limit :limit offset :offset
            ) p
            order by case p.status when 'TEACHER_CONFIRMED' then 0 when 'REOPENED' then 1 when 'DRAFT_AVAILABLE' then 2 else 3 end,
                     p.status_date desc nulls last, lower(p.subject_name), lower(p.section_name), p.section_kind, p.section_id
            """, nativeQuery = true)
    List<CommissionClosureListRow> findPage(@Param("now") LocalDateTime now, @Param("termId") Long termId,
            @Param("pattern") String pattern, @Param("status") String status, @Param("limit") int limit,
            @Param("offset") long offset);
}
