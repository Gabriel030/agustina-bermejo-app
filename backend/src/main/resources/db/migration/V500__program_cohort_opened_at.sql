alter table program_cohort add column opened_at timestamp(6);

update program_cohort set opened_at = created_at;
