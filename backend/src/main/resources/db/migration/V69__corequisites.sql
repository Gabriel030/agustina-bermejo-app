CREATE TABLE program_subject_corequisite (
    id                             BIGSERIAL PRIMARY KEY,
    program_subject_id             BIGINT NOT NULL,
    corequisite_program_subject_id BIGINT NOT NULL,
    CONSTRAINT uk_prog_coreq_subject_coreq
        UNIQUE (program_subject_id, corequisite_program_subject_id),
    CONSTRAINT chk_no_self_coreq
        CHECK (program_subject_id <> corequisite_program_subject_id),
    CONSTRAINT fk_coreq_ps
        FOREIGN KEY (program_subject_id)
        REFERENCES program_subject(id) ON DELETE CASCADE,
    CONSTRAINT fk_coreq_coreq_ps
        FOREIGN KEY (corequisite_program_subject_id)
        REFERENCES program_subject(id) ON DELETE CASCADE
);
