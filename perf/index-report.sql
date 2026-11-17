\pset pager off
\echo === 0. Contexto: tamaño de base y reset de estadísticas
select pg_size_pretty(pg_database_size(current_database())) as db_size, stats_reset from pg_stat_database where datname = current_database();

\echo === 1. Índices sin uso (idx_scan = 0), sin PK ni únicos que sostienen constraints, solo tablas con más de 1000 filas (en tablas vacías no hay señal)
select s.relname as tabla, s.indexrelname as indice, s.idx_scan, pg_size_pretty(pg_relation_size(s.indexrelid)) as tamano, pg_relation_size(s.indexrelid) as bytes, c2.reltuples::bigint as filas_tabla, t.n_tup_ins + t.n_tup_upd + t.n_tup_del as escrituras_tabla, pg_get_indexdef(s.indexrelid) as definicion
from pg_stat_user_indexes s
join pg_index i on i.indexrelid = s.indexrelid
join pg_stat_user_tables t on t.relid = s.relid
join pg_class c2 on c2.oid = s.relid
where s.idx_scan = 0 and c2.reltuples > 1000 and not i.indisprimary
  and not exists (select 1 from pg_constraint c where c.conindid = s.indexrelid)
  and not (i.indisunique)
order by pg_relation_size(s.indexrelid) desc;

\echo === 2. Índices duplicados o redundantes (el prefijo de columnas de A está contenido en B, misma tabla y mismo predicado)
with idx as (
  select i.indexrelid, i.indrelid, c.relname as indice, string_to_array(i.indkey::text, ' ')::int2[] as cols, i.indisunique, i.indisprimary, coalesce(pg_get_expr(i.indpred, i.indrelid), '') as pred, coalesce(pg_get_expr(i.indexprs, i.indrelid), '') as expr, i.indclass::text as opclass, pg_relation_size(i.indexrelid) as bytes
  from pg_index i join pg_class c on c.oid = i.indexrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public'
)
select t.relname as tabla, a.indice as redundante, a.cols as cols_redundante, b.indice as cubierto_por, b.cols as cols_cubridor, pg_size_pretty(a.bytes) as tamano_redundante, (a.cols = b.cols) as identico, pg_get_indexdef(a.indexrelid) as definicion_redundante, pg_get_indexdef(b.indexrelid) as definicion_cubridor
from idx a join idx b on a.indrelid = b.indrelid and a.indexrelid <> b.indexrelid and a.pred = b.pred and a.expr = b.expr
  and b.cols[1:cardinality(a.cols)] = a.cols and (cardinality(b.cols) > cardinality(a.cols) or (a.cols = b.cols and a.indexrelid > b.indexrelid))
join pg_class t on t.oid = a.indrelid
where not a.indisprimary and not a.indisunique
order by a.bytes desc;

\echo === 3. Tablas con seq scans altos (más de 1000 filas)
select u.relname as tabla, k.reltuples::bigint as filas, pg_size_pretty(pg_total_relation_size(u.relid)) as tamano_total, u.seq_scan, u.seq_tup_read, u.idx_scan, u.idx_tup_fetch, case when u.seq_scan + u.idx_scan > 0 then round(100.0 * u.seq_scan / (u.seq_scan + u.idx_scan), 1) end as pct_seq, case when u.seq_scan > 0 then u.seq_tup_read / u.seq_scan end as filas_por_seq
from pg_stat_user_tables u join pg_class k on k.oid = u.relid where k.reltuples > 1000
order by u.seq_tup_read desc limit 25;

\echo === 4. Foreign keys sin índice que las cubra (las columnas de la FK deben ser prefijo de algún índice no parcial)
select cl.relname as tabla, c.conname as fk, (select string_agg(a.attname, ', ' order by k.ord) from unnest(c.conkey) with ordinality k(attnum, ord) join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.attnum) as columnas, rf.relname as referencia, cl.reltuples::bigint as filas, pg_size_pretty(pg_total_relation_size(cl.oid)) as tamano_total
from pg_constraint c
join pg_class cl on cl.oid = c.conrelid
join pg_class rf on rf.oid = c.confrelid
join pg_namespace n on n.oid = cl.relnamespace
where c.contype = 'f' and n.nspname = 'public'
  and not exists (
    select 1 from pg_index i
    where i.indrelid = c.conrelid and i.indpred is null
      and (string_to_array(i.indkey::text, ' ')::int2[])[1:cardinality(c.conkey)] @> c.conkey
      and c.conkey @> (string_to_array(i.indkey::text, ' ')::int2[])[1:cardinality(c.conkey)]
  )
order by cl.reltuples desc, cl.relname, c.conname;

\echo === 5. Índices compuestos y su uso (orden de columnas a revisar)
select s.relname as tabla, s.indexrelname as indice, s.idx_scan, pg_size_pretty(pg_relation_size(s.indexrelid)) as tamano, pg_get_indexdef(s.indexrelid) as definicion
from pg_stat_user_indexes s join pg_index i on i.indexrelid = s.indexrelid
where i.indnkeyatts > 1
order by s.idx_scan, s.relname;
