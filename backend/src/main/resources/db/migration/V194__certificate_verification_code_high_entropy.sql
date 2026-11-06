CREATE FUNCTION certificate_code_migration_tmp() RETURNS VARCHAR AS $$
DECLARE
    alphabet CONSTANT TEXT := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
    first_uuid BYTEA := decode(replace(gen_random_uuid()::TEXT, '-', ''), 'hex');
    second_uuid BYTEA := decode(replace(gen_random_uuid()::TEXT, '-', ''), 'hex');
    random_bytes BYTEA := substring(first_uuid FROM 1 FOR 6) || substring(first_uuid FROM 10 FOR 7)
        || substring(second_uuid FROM 1 FOR 6) || substring(second_uuid FROM 10 FOR 7);
    result TEXT := '';
BEGIN
    FOR position IN 0..25 LOOP
        result := result || substr(alphabet, (get_byte(random_bytes, position) & 31) + 1, 1);
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql VOLATILE;

ALTER TABLE graduation_certificate ALTER COLUMN code TYPE VARCHAR(26);
ALTER TABLE regular_student_certificate ALTER COLUMN code TYPE VARCHAR(26);

UPDATE graduation_certificate
SET code = certificate_code_migration_tmp(), pdf_sha256 = repeat('0', 64);

UPDATE regular_student_certificate
SET code = certificate_code_migration_tmp(), pdf_sha256 = repeat('0', 64);

DROP FUNCTION certificate_code_migration_tmp();

ALTER TABLE graduation_certificate ADD CONSTRAINT ck_graduation_certificate_code_format CHECK (code ~ '^[0-9A-HJKMNP-TV-Z]{26}$');
ALTER TABLE regular_student_certificate ADD CONSTRAINT ck_regular_cert_code_format CHECK (code ~ '^[0-9A-HJKMNP-TV-Z]{26}$');
