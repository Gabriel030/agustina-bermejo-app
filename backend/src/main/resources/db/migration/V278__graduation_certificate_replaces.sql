ALTER TABLE graduation_certificate ADD COLUMN replaces_id BIGINT;
ALTER TABLE graduation_certificate
    ADD CONSTRAINT fk_graduation_certificate_replaces FOREIGN KEY (replaces_id) REFERENCES graduation_certificate(id);
ALTER TABLE graduation_certificate
    ADD CONSTRAINT ck_graduation_certificate_replaces_other CHECK (replaces_id IS NULL OR replaces_id <> id);
ALTER TABLE graduation_certificate
    ADD CONSTRAINT ck_graduation_certificate_revocation_complete
        CHECK (status <> 'REVOKED' OR (revoked_at IS NOT NULL AND revoked_by_id IS NOT NULL AND revocation_reason IS NOT NULL)) NOT VALID;
CREATE UNIQUE INDEX uq_graduation_certificate_replaces ON graduation_certificate (replaces_id) WHERE replaces_id IS NOT NULL;
