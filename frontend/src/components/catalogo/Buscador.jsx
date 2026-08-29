// Genérico — reusable por Épica 8 Fase 3 (grilla de pagos, también paginada,
// ver spec técnica de Épica 7 Fase 3). Estado local: el valor vive en la URL
// (?search=) de la pantalla que lo usa, no acá.
export function Buscador({ value, onChange, placeholder = 'Buscar…' }) {
  return (
    <input
      type="search"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className="w-full rounded-xl border border-border bg-surface px-4 py-2.5 text-[13.5px] text-ink placeholder:text-ink-faint focus:outline-none focus:ring-2 focus:ring-accent/40 md:max-w-xs"
    />
  );
}
