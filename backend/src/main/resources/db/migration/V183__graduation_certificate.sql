CREATE TABLE graduation_certificate (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(16) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    program_enrollment_id BIGINT REFERENCES program_enrollment(id),
    course_enrollment_id BIGINT REFERENCES course_enrollment(id),
    student_id BIGINT NOT NULL REFERENCES student(id),
    status VARCHAR(20) NOT NULL,
    graduation_date DATE NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    issued_by_id BIGINT NOT NULL REFERENCES person(id),
    snapshot JSONB NOT NULL,
    template_ref VARCHAR(40) NOT NULL,
    pdf_sha256 CHAR(64) NOT NULL,
    pdf_path VARCHAR(300) NOT NULL,
    registry_number VARCHAR(20),
    revoked_at TIMESTAMP,
    revoked_by_id BIGINT REFERENCES person(id),
    revocation_reason VARCHAR(500),
    CONSTRAINT ck_graduation_certificate_kind CHECK (kind IN ('PROGRAM', 'COURSE')),
    CONSTRAINT ck_graduation_certificate_status CHECK (status IN ('VALID', 'REVOKED')),
    CONSTRAINT ck_graduation_certificate_one_enrollment
        CHECK ((program_enrollment_id IS NULL) <> (course_enrollment_id IS NULL)),
    CONSTRAINT uq_graduation_certificate_code UNIQUE (code)
);
CREATE UNIQUE INDEX uq_graduation_certificate_program_valid ON graduation_certificate (program_enrollment_id) WHERE status = 'VALID';
CREATE UNIQUE INDEX uq_graduation_certificate_course_valid ON graduation_certificate (course_enrollment_id) WHERE status = 'VALID';
CREATE INDEX ix_graduation_certificate_student ON graduation_certificate (student_id);
