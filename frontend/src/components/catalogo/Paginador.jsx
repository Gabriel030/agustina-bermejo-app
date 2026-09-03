// Genérico — reusable por Épica 8 Fase 3. totalPages ya calculado por el caller
// (Math.ceil(totalItems / porPagina)) para no acoplar este componente al tamaño
// de página elegido por cada pantalla. mt-auto: siempre que el contenedor
// padre sea un flex-col con altura disponible (min-h-full flex-1), el
// Paginador queda anclado al fondo del área de contenido en vez de "subir"
// cuando la página actual tiene menos ítems que el resto — feedback explícito
// del usuario, comportamiento por default para cualquier pantalla que use
// este componente, no algo que cada caller tenga que acordarse de pedir.
export function Paginador({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;

  return (
    <div className="mt-auto flex items-center justify-center gap-3 pt-2">
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
