with
sse as (select e.*, ss.evaluation_scheme_id, ss.term_id, ss.max_absence_percentage, ss.subject_id, t.year_number, t.start_date as tstart, t.end_date as tend,
          es.exam_count_expected, es.tp_count_expected, es.promotion_min_grade, es.min_approval_grade, es.min_regular_grade, es.has_final
        from subject_section_enrollment e join subject_section ss on ss.id = e.program_section_id join term t on t.id = ss.term_id
        left join evaluation_scheme es on es.id = ss.evaluation_scheme_id),
gi as (select program_section_enrollment_id id, count(*) filter (where type='EXAM') exams, count(*) filter (where type='TP') tps,
          min(score) filter (where type='EXAM') min_exam, max(score) filter (where type='FINAL' and sequence_number=1) f1,
          count(*) filter (where type='FINAL' and sequence_number=2) f2, max(date) maxdate
        from grade_item where program_section_enrollment_id is not null group by 1),
att as (select program_section_enrollment_id id, count(*) total, count(*) filter (where status in ('ABSENT','JUSTIFICATION_PENDING')) abs
        from attendance where program_section_enrollment_id is not null group by 1),
pe as (select pe.id, pe.student_id, pe.program_id, c.cursada_start from program_enrollment pe left join program_cohort c on c.id = pe.program_cohort_id),
ps as (select id, program_id, subject_id, curricular_year from program_subject),
checks as (
  select 'A01 aprobada por promoción sin todos los parciales' chk, count(*) n, min(sse.id) ej from sse left join gi on gi.id = sse.id where final_grade_type='PROMOTION' and coalesce(gi.exams,0) < coalesce(sse.exam_count_expected,2)
  union all select 'A02 final sin parciales que den la regularidad', count(*), min(sse.id) from sse left join gi on gi.id = sse.id where final_grade_type='FINAL_EXAM' and coalesce(gi.exams,0) = 0
  union all select 'A03 promoción con un parcial bajo la nota de promoción', count(*), min(sse.id) from sse join gi on gi.id = sse.id where final_grade_type='PROMOTION' and gi.min_exam < coalesce(sse.promotion_min_grade, 99)
  union all select 'A04 promoción en un esquema que no promociona', count(*), min(id) from sse where final_grade_type='PROMOTION' and promotion_min_grade is null
  union all select 'A05 segundo final con el primero aprobado', count(*), min(sse.id) from sse join gi on gi.id = sse.id where gi.f2 > 0 and gi.f1 >= coalesce(sse.min_approval_grade, 4)
  union all select 'A06 aprobada por final sin nota de final o con final bajo', count(*), min(sse.id) from sse left join gi on gi.id = sse.id where status='APPROVED' and final_grade_type='FINAL_EXAM' and (gi.f1 is null or (gi.f1 < coalesce(sse.min_approval_grade,4) and coalesce(gi.f2,0) = 0))
  union all select 'A07 aprobada sin tener aprobada una correlativa antes', count(*), min(x.id) from (
      select distinct sse.id from sse join pe on pe.id = sse.program_enrollment_id join ps on ps.program_id = pe.program_id and ps.subject_id = sse.subject_id
      join program_subject_prerequisite pr on pr.program_subject_id = ps.id join ps p2 on p2.id = pr.prerequisite_program_subject_id
      where sse.status = 'APPROVED' and not exists (select 1 from sse s2 where s2.program_enrollment_id = sse.program_enrollment_id and s2.subject_id = p2.subject_id and s2.status = 'APPROVED' and s2.year_number < sse.year_number)) x
  union all select 'A08 cursada antes del año que le corresponde en el plan', count(*), min(sse.id) from sse join pe on pe.id = sse.program_enrollment_id join ps on ps.program_id = pe.program_id and ps.subject_id = sse.subject_id
      where pe.cursada_start is not null and sse.year_number < extract(year from pe.cursada_start) + ps.curricular_year - 1
  union all select 'A09 inscripción activa a mesa con la materia ya aprobada', count(*), min(r.id) from exam_board_registration r join sse on sse.id = r.program_section_enrollment_id where sse.status = 'APPROVED' and r.status = 'REGISTERED'
  union all select 'A10 en curso en un ciclo que ya terminó', count(*), min(id) from sse where status = 'IN_PROGRESS' and tend < current_date
  union all select 'A11 notas con fecha futura', count(*), min(id) from grade_item where date > now()
  union all select 'A12 aprobada con inasistencias por encima del máximo', count(*), min(sse.id) from sse join att on att.id = sse.id where status = 'APPROVED' and att.total > 0 and 100.0 * att.abs / att.total > coalesce(sse.max_absence_percentage * 100, 25)
  union all select 'A13 cursada terminada sin ninguna asistencia registrada', count(*), min(sse.id) from sse left join att on att.id = sse.id where status in ('APPROVED','FAILED') and att.id is null
  union all select 'A14 asistencia en fecha futura', count(*), min(id) from attendance where class_date > current_date
  union all select 'A15 asistencia fuera del período del ciclo', count(*), min(a.id) from attendance a join sse on sse.id = a.program_section_enrollment_id where a.class_date not between sse.tstart and sse.tend
  union all select 'A16 en curso sin ninguna nota cargada a esta altura del año', count(*), min(sse.id) from sse left join gi on gi.id = sse.id where status = 'IN_PROGRESS' and gi.id is null
  union all select 'A17 desaprobada sin parciales ni final que lo expliquen', count(*), min(sse.id) from sse left join gi on gi.id = sse.id where status = 'FAILED' and coalesce(gi.exams,0) = 0
  union all select 'A18 materia de años anteriores nunca cursada (plan atrasado sin motivo)', count(*), min(pe.id) from pe join ps on ps.program_id = pe.program_id
      where pe.cursada_start is not null and extract(year from pe.cursada_start) + ps.curricular_year - 1 < 2026
      and not exists (select 1 from sse where sse.program_enrollment_id = pe.id and sse.subject_id = ps.subject_id)
  union all select 'A19 TP cargados por encima de los previstos en el esquema', count(*), min(sse.id) from sse join gi on gi.id = sse.id where gi.tps > coalesce(sse.tp_count_expected, 99)
  union all select 'B01 factura pagada con saldo pendiente', count(*), min(id) from invoice where payment_status = 'PAID' and outstanding_amount > 0
  union all select 'B02 factura vencida sin saldo pendiente', count(*), min(id) from invoice where payment_status = 'OVERDUE' and outstanding_amount <= 0
  union all select 'B03 factura pendiente vencida hace mas de 30 dias', count(*), min(id) from invoice where payment_status = 'PENDING' and document_type = 'INVOICE' and due_date < current_date - 30
  union all select 'B04 pago sin factura cubierta ni credito en cuenta', count(*), min(p.id) from payment p where not exists (select 1 from payment_allocation_source s where s.payment_id = p.id) and not exists (select 1 from current_account_entry e where e.payment_id = p.id)
  union all select 'B05 saldo de cuenta distinto de la suma de facturas impagas', count(*), min(a.id) from student_current_account a where a.balance_side = 'DEBIT' and a.balance_amount <> (select coalesce(sum(i.outstanding_amount),0) from invoice i where i.student_current_account_id = a.id)
  union all select 'C01 curso en curso sin notas', count(*), min(c.id) from course_section_enrollment c where status = 'IN_PROGRESS' and not exists (select 1 from grade_item g where g.course_section_enrollment_id = c.id)
  union all select 'D01 mesa ya tomada que sigue programada', count(*), min(id) from exam_board where status = 'SCHEDULED' and exam_date < now()
  union all select 'D02 nota de final sin mesa asociada', count(*), min(id) from grade_item where type = 'FINAL' and exam_board_id is null
  union all select 'E01 egreso pedido sin el plan completo', count(*), min(g.id) from graduation_request g where exists (
      select 1 from program_enrollment p join program_subject ps on ps.program_id = p.program_id where p.id = g.program_enrollment_id
      and not exists (select 1 from subject_section_enrollment e join subject_section s on s.id = e.program_section_id where e.program_enrollment_id = p.id and s.subject_id = ps.subject_id and e.status = 'APPROVED'))
  union all select 'E02 excepción sobre una materia que no es del plan del alumno', count(*), min(d.id) from director_approval_request d join program_enrollment p on p.id = d.program_enrollment_id
      where d.subject_id is not null and not exists (select 1 from program_subject ps where ps.program_id = p.program_id and ps.subject_id = d.subject_id)
  union all select 'E03 dispensa de correlativa con la correlativa ya aprobada', count(*), min(d.id) from director_approval_request d join program_enrollment p on p.id = d.program_enrollment_id
      join program_subject ps on ps.program_id = p.program_id and ps.subject_id = d.subject_id join program_subject_prerequisite pr on pr.program_subject_id = ps.id
      join program_subject req on req.id = pr.prerequisite_program_subject_id
      where d.type = 'PREREQUISITE_WAIVER' and d.status = 'REQUESTED' and exists (select 1 from subject_section_enrollment e join subject_section s on s.id = e.program_section_id where e.program_enrollment_id = p.id and s.subject_id = req.subject_id and e.status = 'APPROVED')
  union all select 'E04 curso terminado que sigue en curso', count(*), min(ce.id) from course_enrollment ce join course c on c.id = ce.course_id
      where ce.status = 'IN_PROGRESS' and date '2026-03-09' + (c.duration_months || ' months')::interval - interval '3 days' < current_date
  union all select 'E06 regular inscripto a una mesa de otra materia', count(*), min(r.id) from exam_board_registration r join subject_section_enrollment e on e.id = r.program_section_enrollment_id
      join subject_section s on s.id = e.program_section_id join exam_board b on b.id = r.exam_board_id where b.subject_id <> s.subject_id
  union all select 'E07 inscripción activa a mesa sin estar regular', count(*), min(r.id) from exam_board_registration r join subject_section_enrollment e on e.id = r.program_section_enrollment_id
      where r.status = 'REGISTERED' and e.status <> 'REGULAR'
)
select chk, n, ej from checks order by chk;
