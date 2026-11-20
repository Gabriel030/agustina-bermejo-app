create index ix_import_row_committed_natural_key on import_row (entity_type, natural_key, content_hash) where status = 'COMMITTED';
