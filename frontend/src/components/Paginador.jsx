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
        className="px-3 text-[12.5px] font-semibold text-ink disabled:opacity-40">
        ← Anterior
      </Button>
      <span className="text-[12.5px] text-ink-faint">
        Página {page} de {totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        type="button"
        disabled={page >= totalPages}
        onClick={() => onChange(page + 1)}
        className="px-3 text-[12.5px] font-semibold text-ink disabled:opacity-40">
        Siguiente →
      </Button>
    </div>
  );
}import { Button } from '@/components/ui/button';
