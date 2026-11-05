alter table exam_board add column canceled_at timestamp(6);
alter table exam_board add column cancel_reason varchar(255);
alter table exam_board add column rescheduled_from timestamp(6);
