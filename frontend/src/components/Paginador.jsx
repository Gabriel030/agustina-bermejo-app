export function Paginador({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-center gap-3 pt-2">
      <Button
        variant="outline"
        size="sm"
        type="button"
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
        className="min-h-11 px-3 text-size-12-5 font-semibold text-ink disabled:text-(--disabled-fg) md:min-h-0">
        ← Anterior
      </Button>
      <span className="text-size-12-5 text-ink-faint">
        Página {page} de {totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        type="button"
        disabled={page >= totalPages}
        onClick={() => onChange(page + 1)}
        className="min-h-11 px-3 text-size-12-5 font-semibold text-ink disabled:text-(--disabled-fg) md:min-h-0">
        Siguiente →
      </Button>
    </div>
  );
}import { Button } from '@/components/ui/button';
