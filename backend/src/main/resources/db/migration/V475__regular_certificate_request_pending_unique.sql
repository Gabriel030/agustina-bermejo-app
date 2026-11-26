create unique index uq_regular_cert_request_pending_per_enrollment
    on regular_certificate_request (kind, (coalesce(program_enrollment_id, course_enrollment_id)))
    where outcome = 'PENDING_STAFF';
