alter table subject_section_enrollment add column waitlist_expiry_reminded_at timestamp(6);
alter table course_section_enrollment add column waitlist_expiry_reminded_at timestamp(6);
alter table course_enrollment add column waitlist_expiry_reminded_at timestamp(6);
