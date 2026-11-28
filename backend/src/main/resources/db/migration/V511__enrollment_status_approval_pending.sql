alter table course_enrollment drop constraint course_enrollment_status_enum;
alter table course_enrollment add constraint course_enrollment_status_enum check (status in ('APPROVAL_PENDING', 'APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN')) not valid;
alter table course_section_enrollment drop constraint course_section_enrollment_status_enum;
alter table course_section_enrollment add constraint course_section_enrollment_status_enum check (status in ('APPROVAL_PENDING', 'APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN')) not valid;
alter table subject_section_enrollment drop constraint subject_section_enrollment_status_enum;
alter table subject_section_enrollment add constraint subject_section_enrollment_status_enum check (status in ('APPROVAL_PENDING', 'APPROVED', 'ENROLLED', 'FAILED', 'IN_PROGRESS', 'REGULAR', 'WAITLISTED', 'WAITLIST_EXPIRED', 'WITHDRAWN')) not valid;
