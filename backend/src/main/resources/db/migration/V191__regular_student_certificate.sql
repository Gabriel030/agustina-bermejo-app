CREATE TABLE regular_certificate_request (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    student_id BIGINT NOT NULL REFERENCES student(id),
    kind VARCHAR(20) NOT NULL,
    program_enrollment_id BIGINT REFERENCES program_enrollment(id),
    course_enrollment_id BIGINT REFERENCES course_enrollment(id),
    requested_by_id BIGINT NOT NULL REFERENCES person(id),
    requested_at TIMESTAMP NOT NULL,
    channel VARCHAR(20) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    certificate_id BIGINT,
    CONSTRAINT ck_regular_request_one_enrollment CHECK ((program_enrollment_id IS NULL) <> (course_enrollment_id IS NULL))
);

CREATE TABLE regular_student_certificate (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(16) NOT NULL UNIQUE,
    kind VARCHAR(20) NOT NULL,
    program_enrollment_id BIGINT REFERENCES program_enrollment(id),
    course_enrollment_id BIGINT REFERENCES course_enrollment(id),
    student_id BIGINT NOT NULL REFERENCES student(id),
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    validity_days INT NOT NULL,
    issued_by_id BIGINT NOT NULL REFERENCES person(id),
    issue_channel VARCHAR(20) NOT NULL,
    request_id BIGINT NOT NULL REFERENCES regular_certificate_request(id),
    billing_mode_at_issue VARCHAR(10) NOT NULL,
    standing_basis VARCHAR(30) NOT NULL,
    standing_confirmed_by_id BIGINT REFERENCES person(id),
    standing_confirmed_at TIMESTAMP,
    standing_evidence VARCHAR(500),
    exception_id BIGINT,
    snapshot JSONB NOT NULL,
    template_ref VARCHAR(40) NOT NULL,
    registry_number VARCHAR(20),
    pdf_sha256 VARCHAR(64) NOT NULL,
    pdf_path VARCHAR(300) NOT NULL,
    revoked_at TIMESTAMP,
    revoked_by_id BIGINT REFERENCES person(id),
    revocation_reason VARCHAR(500),
    CONSTRAINT ck_regular_cert_one_enrollment CHECK ((program_enrollment_id IS NULL) <> (course_enrollment_id IS NULL)),
    CONSTRAINT ck_regular_cert_manual_has_evidence CHECK (standing_basis <> 'MANUALLY_CONFIRMED'
        OR (standing_confirmed_by_id IS NOT NULL AND standing_confirmed_at IS NOT NULL AND standing_evidence IS NOT NULL))
);

ALTER TABLE regular_certificate_request ADD CONSTRAINT fk_regular_request_certificate
    FOREIGN KEY (certificate_id) REFERENCES regular_student_certificate(id);

CREATE UNIQUE INDEX uq_regular_cert_program_valid ON regular_student_certificate (program_enrollment_id) WHERE status = 'VALID';
CREATE UNIQUE INDEX uq_regular_cert_course_valid ON regular_student_certificate (course_enrollment_id) WHERE status = 'VALID';
CREATE INDEX ix_regular_cert_student ON regular_student_certificate (student_id);
CREATE INDEX ix_regular_request_student ON regular_certificate_request (student_id);
