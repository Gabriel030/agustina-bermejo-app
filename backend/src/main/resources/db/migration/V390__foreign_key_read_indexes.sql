create index if not exists ix_exam_board_subject_term on exam_board (subject_id, term_id);
create index if not exists ix_grade_item_exam_board on grade_item (exam_board_id);
create index if not exists ix_subject_section_subject on subject_section (subject_id);
create index if not exists ix_student_document_student on student_document (student_id);
create index if not exists ix_program_enrollment_program on program_enrollment (program_id);
create index if not exists ix_invoice_line_exam_board_registration on invoice_line (exam_board_registration_id) where exam_board_registration_id is not null;
create index if not exists ix_invoice_line_subject on invoice_line (subject_id) where subject_id is not null;
create index if not exists ix_invoice_line_price_version on invoice_line (price_version_id) where price_version_id is not null;
