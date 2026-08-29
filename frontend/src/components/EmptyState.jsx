// Estado vacío (ícono/emoji + título + descripción, acción opcional) — antes
// reescrito a mano en Placeholder, Mis carreras (sin carreras), Novedades de
// Inicio y Mis solicitudes. `icon` es un emoji suelto (ej. "🔔") — no un ícono
// de lucide, para mantener el tono ilustrativo que ya tenían estos bloques.
export function EmptyState({ icon, title, description, action, className }) {
  return (
    <div className={`flex flex-1 flex-col items-center justify-center gap-2 py-8 text-center ${className ?? ''}`}>
      {icon && <span className="text-2xl">{icon}</span>}
      <div className="font-heading text-[15px] font-semibold text-ink">{title}</div>
      {description && <div className="max-w-sm text-sm text-ink-faint">{description}</div>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
