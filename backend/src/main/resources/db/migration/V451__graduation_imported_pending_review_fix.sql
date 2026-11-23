update graduation_request gr
set certificate_review_status = 'PENDING_REVIEW'
where gr.status = 'APPROVED'
  and gr.decided_by_id is null
  and gr.certificate_review_status = 'NOT_REQUIRED'
  and not exists (
      select 1 from graduation_certificate gc
      where gc.program_enrollment_id = gr.program_enrollment_id
        and gc.status in ('VALID', 'REVOKED'));

update graduation_request gr
set certificate_review_status = 'ENABLED'
where gr.status = 'APPROVED'
  and gr.decided_by_id is null
  and gr.certificate_review_status = 'NOT_REQUIRED'
  and exists (
      select 1 from graduation_certificate gc
      where gc.program_enrollment_id = gr.program_enrollment_id
        and gc.status in ('VALID', 'REVOKED'));
