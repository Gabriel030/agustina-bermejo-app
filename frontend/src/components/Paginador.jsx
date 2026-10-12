export function Paginador({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-center gap-3 pt-2">
      <button
        type="button"
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
        className="rounded-lg border border-border bg-surface px-3 py-1.5 text-[12.5px] font-semibold text-ink disabled:opacity-40"
      >
        ← Anterior
      </button>
      <span className="text-[12.5px] text-ink-faint">
        Página {page} de {totalPages}
      </span>
      <button
        type="button"
        disabled={page >= totalPages}
        onClick={() => onChange(page + 1)}
        className="rounded-lg border border-border bg-surface px-3 py-1.5 text-[12.5px] font-semibold text-ink disabled:opacity-40"
      >
        Siguiente →
      </button>
    </div>
  );
}
